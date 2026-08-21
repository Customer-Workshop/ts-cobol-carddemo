/**
 * AID-key parity (FR-S01-20): the 3270 screens map ENTER and PF3 and reject
 * every other AID with CCDA-MSG-INVALID-KEY (app/cpy/CSMSG01Y.cpy). In the web
 * target, F3 maps to the Exit action and any other function key is the
 * "unmapped AID" equivalent.
 */
export const MSG_INVALID_KEY = 'Invalid key pressed. Please see below...';

export type AidKeyAction = 'exit' | 'invalid' | null;

const FUNCTION_KEY = /^F([1-9]|1[0-2])$/;

export function classifyAidKey(event: KeyboardEvent): AidKeyAction {
  if (!FUNCTION_KEY.test(event.key)) {
    return null;
  }
  return event.key === 'F3' ? 'exit' : 'invalid';
}
