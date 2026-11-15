import axios from 'axios'
import type {
  QuestionAddRequest,
  QuestionBatchDeleteRequest,
  QuestionQueryRequest,
  QuestionUpdateRequest,
} from '@generated'
import { OpenAPI } from '@generated'

const base = () => OpenAPI.BASE.replace(/\/+$/, '')
export const pageManagedQuestions = async (body: QuestionQueryRequest) =>
  (await axios.post(`${base()}/question/manage/page`, body)).data
export const updateManagedQuestion = async (body: QuestionUpdateRequest) =>
  (await axios.post(`${base()}/question/manage/update`, body)).data
export const deleteManagedQuestions = async (body: QuestionBatchDeleteRequest) =>
  (await axios.post(`${base()}/question/manage/delete/batch`, body)).data
export const preflightManagedQuestion = async (body: QuestionAddRequest) =>
  (await axios.post(`${base()}/question/manage/preflight`, body)).data
