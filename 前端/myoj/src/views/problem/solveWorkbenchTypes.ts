export type SolveRunCase = {
  index: number
  input: string
  expectedOutput: string | null
  actualOutput: string
  passed: boolean | null
}

export type SolveRunResult = {
  mode?: 'SAMPLE' | 'CUSTOM' | string | null
  sandboxCode?: number | null
  sandboxMessage?: string | null
  judgeInfo?: {
    message?: string | null
    detail?: string | null
    time?: number | null
    memory?: number | null
  } | null
  terminalError: boolean
  cases: SolveRunCase[]
  allSamplePassed: boolean | null
  warning?: string | null
  hints?: string[] | null
  suspectedShortcut?: boolean
}

export type SolveSubmitItem = {
  questionId?: string
  submitNo?: number
  globalSubmitNo?: number
  language?: string
  status?: number
  judgeInfo?: string
  createTime?: string
}

export type SolveSyntaxIssue = {
  line: number
  column: number
  message: string
  severity: 'ERROR' | 'WARNING'
}
