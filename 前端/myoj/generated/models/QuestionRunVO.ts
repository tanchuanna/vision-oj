/* generated using openapi-typescript-codegen -- do not edit */
/* istanbul ignore file */
/* tslint:disable */
/* eslint-disable */
import type { JudgeInfo } from './JudgeInfo'
import type { QuestionRunCaseVO } from './QuestionRunCaseVO'

export type QuestionRunVO = {
    mode?: string;
    sandboxCode?: number;
    sandboxMessage?: string;
    judgeInfo?: JudgeInfo;
    terminalError?: boolean;
    cases?: Array<QuestionRunCaseVO>;
    allSamplePassed?: boolean | null;
    warning?: string;
    hints?: Array<string>;
    suspectedShortcut?: boolean;
};

