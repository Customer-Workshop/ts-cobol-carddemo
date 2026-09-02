import { ElementRef } from '@angular/core';
import { UserAdminSeverity } from './user-admin.service';

export { MSG_INVALID_KEY } from '../shared/invalid-key';

/** Route of the admin menu (COADM01C), the default RETURN-TO-PREV-SCREEN target of every COUSR screen. */
export const ADMIN_MENU_ROUTE = '/admin';
/** Route of COUSR00C, the caller recorded in CDEMO-FROM-PROGRAM when a row is selected with U or D. */
export const USER_LIST_ROUTE = '/admin/users';
export const USER_ADD_ROUTE = '/admin/users/add';
export const USER_UPDATE_ROUTE = '/admin/users/update';
export const USER_DELETE_ROUTE = '/admin/users/delete';

export const PROGRAM_USER_LIST = 'COUSR00C';
export const PROGRAM_USER_UPDATE = 'COUSR02C';
export const PROGRAM_USER_DELETE = 'COUSR03C';

/** BMS field lengths shared by COUSR0A..COUSR3A (app/bms/COUSR0*.bms). */
export const LEN_USER_ID = 8;
export const LEN_NAME = 20;
export const LEN_PASSWORD = 8;
export const LEN_USER_TYPE = 1;
export const LEN_SELECT = 1;
export const LEN_MESSAGE = 78;

export type FunctionKey = 'F1' | 'F2' | 'F3' | 'F4' | 'F5' | 'F6' | 'F7' | 'F8' | 'F9' | 'F10' | 'F11' | 'F12';

const FUNCTION_KEY = /^F([1-9]|1[0-2])$/;

/**
 * 3270 AID classification for screens that map more than ENTER/PF3
 * (COUSR00C: PF7/PF8; COUSR01C: PF4; COUSR02C/03C: PF4/PF5/PF12). Returns the
 * function key name so each screen applies its own EVALUATE EIBAID, or null for
 * ordinary typing.
 */
export function functionKeyOf(event: KeyboardEvent): FunctionKey | null {
  return FUNCTION_KEY.test(event.key) ? (event.key as FunctionKey) : null;
}

/** The COBOL `MOVE -1 TO xxxL`: put the cursor on the field named by the API. */
export function focusField(host: ElementRef<HTMLElement>, field: string | null | undefined): void {
  if (!field) {
    return;
  }
  const input = host.nativeElement.querySelector<HTMLElement>(`[data-field="${field}"]`);
  input?.focus();
}

/** Return route for RETURN-TO-PREV-SCREEN given the caller carried in the `from` query param. */
export function returnRouteFor(from: string | null | undefined): string {
  return from === PROGRAM_USER_LIST ? USER_LIST_ROUTE : ADMIN_MENU_ROUTE;
}

export function severityClass(severity: UserAdminSeverity | null): Record<string, boolean> {
  return {
    'error-message': severity === 'error',
    'success-message': severity === 'success',
    'neutral-message': severity === 'neutral'
  };
}
