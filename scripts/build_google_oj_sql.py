"""Build a reviewable MySQL import from Google's official competition archive.

The source checkout is deliberately kept outside Git in .data-sources.  Only
small, locally available blobs are read; missing or unsupported cases are
reported, never silently replaced with invented tests.
"""

from __future__ import annotations

import argparse
import base64
import csv
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path
from urllib.parse import quote

SOURCE = "google-coding-competitions-archive"
AUTHOR_ID = 1999990000000000001
MAX_CASE_CHARS = 20_000
MAX_HIDDEN_CASES = 24


class GitBlobs:
    def __init__(self, repo: Path):
        self.repo = repo
        self.env = {**os.environ, "GIT_NO_LAZY_FETCH": "1"}
        raw = subprocess.check_output(
            ["git", "ls-tree", "-r", "-z", "HEAD"], cwd=repo, env=self.env
        )
        self.objects: dict[str, str] = {}
        for item in raw.split(b"\0"):
            if not item:
                continue
            meta, path = item.split(b"\t", 1)
            self.objects[path.decode("utf-8")] = meta.split()[-1].decode("ascii")
        self.proc = subprocess.Popen(
            ["git", "cat-file", "--batch"],
            cwd=repo,
            env=self.env,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        )

    def read(self, path: str) -> bytes | None:
        oid = self.objects.get(path)
        if oid is None:
            return None
        assert self.proc.stdin and self.proc.stdout
        self.proc.stdin.write((oid + "\n").encode("ascii"))
        self.proc.stdin.flush()
        header = self.proc.stdout.readline().split()
        if len(header) != 3 or header[1] != b"blob":
            return None
        size = int(header[2])
        data = self.proc.stdout.read(size)
        self.proc.stdout.read(1)
        return data

    def close(self) -> None:
        if self.proc.stdin:
            self.proc.stdin.close()
        self.proc.wait(timeout=10)


def decode_case(data: bytes | None, *, output: bool = False) -> str | None:
    if data is None:
        return None
    try:
        value = data.decode("utf-8").replace("\r\n", "\n")
    except UnicodeDecodeError:
        return None
    if not value.strip() or len(value) > MAX_CASE_CHARS or "\x00" in value:
        return None
    return value.rstrip("\n") if output else value


def b64_expr(value: str) -> str:
    return "CONVERT(FROM_BASE64('%s') USING utf8mb4)" % base64.b64encode(
        value.encode("utf-8")
    ).decode("ascii")


def sql_string(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def statement_html(raw: bytes, path: str) -> str | None:
    try:
        html = raw.decode("utf-8")
    except UnicodeDecodeError:
        return None
    body = re.search(r"<body[^>]*>(.*?)</body>", html, re.I | re.S)
    if body:
        html = body.group(1)
    html = re.sub(r"<(script|style)\b[^>]*>.*?</\1>", "", html, flags=re.I | re.S)
    html = re.sub(r"\s+on\w+\s*=\s*(['\"]).*?\1", "", html, flags=re.I | re.S)
    base = path.rsplit("/", 1)[0]

    def rewrite_image(match: re.Match[str]) -> str:
        before, src, after = match.groups()
        if re.match(r"^(https?:|data:|/)", src, re.I):
            return match.group(0)
        absolute = f"https://raw.githubusercontent.com/google/coding-competitions-archive/main/{quote(base + '/' + src)}"
        return before + absolute + after

    html = re.sub(r"(<img\b[^>]*\bsrc\s*=\s*['\"])([^'\"]+)(['\"])", rewrite_image, html, flags=re.I)
    return html.strip() if len(html.strip()) >= 20 else None


def paired_cases(blobs: GitBlobs, paths: list[str], sample: bool) -> list[dict[str, object]]:
    pairs: list[dict[str, object]] = []
    for path in sorted(paths):
        if not path.endswith(".in"):
            continue
        output_path = path[:-3] + ".ans"
        if output_path not in blobs.objects:
            continue
        inp = decode_case(blobs.read(path))
        out = decode_case(blobs.read(output_path), output=True)
        if inp is not None and out is not None:
            pairs.append({"input": inp, "output": out, "sample": sample})
    return pairs


def statement_samples(blobs: GitBlobs, prefix: str) -> list[dict[str, object]]:
    paths = [p for p in blobs.objects if p.startswith(prefix + "/problem_statement/")]
    result: list[dict[str, object]] = []
    for path in sorted(paths):
        if not re.search(r"sample.*_input\.txt$", path, re.I):
            continue
        output_path = re.sub(r"_input\.txt$", "_output.txt", path, flags=re.I)
        inp = decode_case(blobs.read(path))
        out = decode_case(blobs.read(output_path), output=True)
        if inp is not None and out is not None:
            result.append({"input": inp, "output": out, "sample": True})
    return result


def build(args: argparse.Namespace) -> None:
    blobs = GitBlobs(args.repo)
    output = args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    report: list[tuple[str, str]] = []
    imported = 0
    with output.open("w", encoding="utf-8", newline="\n") as sql:
        sql.write("-- Generated from Google coding-competitions-archive; Apache-2.0.\n")
        sql.write("-- Import into a backed-up test database only; do not claim original judge equivalence.\n")
        sql.write("SET NAMES utf8mb4;\n")
        sql.write("CREATE TABLE IF NOT EXISTS question_import_source (\n")
        sql.write("  question_id BIGINT NOT NULL PRIMARY KEY, source_key VARCHAR(80) NOT NULL,\n")
        sql.write("  external_id VARCHAR(255) NOT NULL, source_url VARCHAR(1024) NOT NULL,\n")
        sql.write("  license_id VARCHAR(64) NOT NULL, source_commit CHAR(40) NOT NULL,\n")
        sql.write("  imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,\n")
        sql.write("  UNIQUE KEY uk_question_import_source (source_key, external_id)\n")
        sql.write(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;\n")
        sql.write("START TRANSACTION;\n")
        revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=args.repo).decode().strip()
        for yaml_path in sorted(p for p in blobs.objects if p.endswith("/problem.yaml")):
            prefix = yaml_path[:-len("/problem.yaml")]
            raw = blobs.read(yaml_path)
            if raw is None:
                report.append((prefix, "missing metadata blob"))
                continue
            meta = raw.decode("utf-8", errors="replace")
            if re.search(r"^validation:\s*custom", meta, re.M | re.I):
                report.append((prefix, "custom or interactive validator"))
                continue
            title_match = re.search(r"^name:\s*(.+)$", meta, re.M)
            if not title_match:
                report.append((prefix, "missing title"))
                continue
            title = title_match.group(1).strip().strip("'\"")[:255]
            statement_path = prefix + "/problem_statement/problem.html"
            statement_raw = blobs.read(statement_path)
            content = statement_html(statement_raw, statement_path) if statement_raw else None
            if content is None:
                report.append((prefix, "missing or unreadable statement"))
                continue
            samples = statement_samples(blobs, prefix)
            if not samples:
                samples = paired_cases(blobs, [p for p in blobs.objects if p.startswith(prefix + "/data/sample/")], True)
            secrets = paired_cases(blobs, [p for p in blobs.objects if p.startswith(prefix + "/data/secret/")], False)
            if not samples or not secrets:
                report.append((prefix, "missing usable public or hidden case"))
                continue
            if len(secrets) > MAX_HIDDEN_CASES:
                report.append((prefix, "more hidden cases than reviewed import limit"))
                continue
            cases = samples + secrets
            source_url = "https://github.com/google/coding-competitions-archive/tree/" + revision + "/" + quote(prefix)
            content += ("\n<p class=\"problem-source\">Source: "
                        f"<a href=\"{source_url}\">Google Coding Competitions Archive</a> "
                        "(Apache-2.0). Results here are judged with the archived test cases.</p>")
            answer = "Original archive: " + source_url
            judge_config = {"timeLimit": 5000, "memoryLimit": 262144, "stackLimit": 8192}
            float_match = re.search(r"^output_validator_flags:\s*float_tolerance\s+([0-9.eE+-]+)", meta, re.M)
            if float_match:
                judge_config["floatTolerance"] = float(float_match.group(1))
            qid = 8_100_000_000_000_000_000 + int(hashlib.sha256(prefix.encode()).hexdigest()[:12], 16)
            tags = ["Google Coding Competitions", prefix.split("/")[0], prefix.split("/")[1]]
            sql.write(f"-- {prefix}\n")
            sql.write("INSERT INTO question (id,title,content,question_type,tags,answer,submit_num,accepted_num,judge_case,judge_config,thumb_num,favour_num,user_id,create_time,update_time,is_delete)\n")
            sql.write("SELECT %d,%s,%s,'TEXT',%s,%s,0,0,%s,%s,0,0,%d,NOW(),NOW(),0\n" % (
                qid, sql_string(title), b64_expr(content), b64_expr(json.dumps(tags, ensure_ascii=False)),
                b64_expr(answer), b64_expr(json.dumps(cases, ensure_ascii=False)),
                b64_expr(json.dumps(judge_config)), AUTHOR_ID,
            ))
            sql.write("FROM user WHERE id=%d AND user_role='admin' AND is_delete=0\n" % AUTHOR_ID)
            sql.write("AND NOT EXISTS (SELECT 1 FROM question_import_source WHERE source_key=%s AND external_id=%s);\n" % (sql_string(SOURCE), sql_string(prefix)))
            sql.write("INSERT INTO question_import_source (question_id,source_key,external_id,source_url,license_id,source_commit)\n")
            sql.write("SELECT %d,%s,%s,%s,'Apache-2.0',%s FROM question WHERE id=%d\n" % (
                qid, sql_string(SOURCE), sql_string(prefix), sql_string(source_url), sql_string(revision), qid,
            ))
            sql.write("AND NOT EXISTS (SELECT 1 FROM question_import_source WHERE source_key=%s AND external_id=%s);\n" % (sql_string(SOURCE), sql_string(prefix)))
            imported += 1
            report.append((prefix, "imported"))
        sql.write("COMMIT;\n")
        sql.write("SELECT COUNT(*) AS imported_google_questions FROM question_import_source WHERE source_key='google-coding-competitions-archive';\n")
    with args.report.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["source_path", "status"])
        writer.writerows(report)
    blobs.close()
    print(f"Imported candidates: {imported}; skipped: {len(report) - imported}")
    print(f"SQL: {output}; report: {args.report}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    build(parser.parse_args())
