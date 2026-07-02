import { badRequest } from './zod-validation';

export interface PageParams {
  page: number;
  pageSize: number;
  skip: number;
  take: number;
}

export interface PageResult<T> {
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
  items: T[];
}

export function parsePageParams(pageRaw?: string, pageSizeRaw?: string): PageParams {
  const page = pageRaw === undefined ? 1 : Number(pageRaw);
  const pageSize = pageSizeRaw === undefined ? 20 : Number(pageSizeRaw);
  if (!Number.isInteger(page) || page < 1) {
    badRequest('page must be an integer >= 1', [{ field: 'page', message: 'must be an integer >= 1' }]);
  }
  if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100) {
    badRequest('pageSize must be an integer between 1 and 100', [
      { field: 'pageSize', message: 'must be an integer between 1 and 100' },
    ]);
  }
  return { page, pageSize, skip: (page - 1) * pageSize, take: pageSize };
}

export function pageResult<T>(params: PageParams, totalItems: number, items: T[]): PageResult<T> {
  return {
    page: params.page,
    pageSize: params.pageSize,
    totalItems,
    totalPages: Math.ceil(totalItems / params.pageSize),
    items,
  };
}
