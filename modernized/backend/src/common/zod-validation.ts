import { BadRequestException } from '@nestjs/common';
import { ZodError, ZodSchema } from 'zod';

export interface FieldIssue {
  field: string;
  message: string;
}

/** Throws a 400 in the openapi.yaml ErrorResponse shape (with field-level details). */
export function badRequest(message: string, details?: FieldIssue[]): never {
  throw new BadRequestException({
    statusCode: 400,
    error: 'Bad Request',
    message,
    ...(details && details.length > 0 ? { details } : {}),
  });
}

export function zodIssues(error: ZodError): FieldIssue[] {
  return error.issues.map((i) => ({ field: i.path.join('.') || '(root)', message: i.message }));
}

export function parseWith<T>(schema: ZodSchema<T>, data: unknown): T {
  const result = schema.safeParse(data);
  if (!result.success) {
    badRequest('Validation failed', zodIssues(result.error));
  }
  return result.data;
}
