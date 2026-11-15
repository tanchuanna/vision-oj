/* generated using openapi-typescript-codegen -- do not edit */
import type { SubmissionListItemVO } from './SubmissionListItemVO'
import type { OrderItem } from './OrderItem'

export type PageSubmissionListItemVO = {
    records?: Array<SubmissionListItemVO>;
    total?: number;
    size?: number;
    current?: number;
    orders?: Array<OrderItem>;
    pages?: number;
};
