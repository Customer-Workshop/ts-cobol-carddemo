/**
 * Business logic extracted from COSGN00C.cbl
 * Signon screen validation for the CardDemo application.
 */
import { SecUserData } from '../models/records';

export type SignOnResult =
  | { success: true; targetProgram: string; userType: string }
  | { success: false; errorMessage: string };

/**
 * Validates user ID input.
 * COSGN00C lines 117-122: checks if USERIDI is SPACES or LOW-VALUES.
 */
export function validateUserId(userId: string): string | null {
  if (!userId || userId.trim() === '') {
    return 'Please enter User ID ...';
  }
  return null;
}

/**
 * Validates password input.
 * COSGN00C lines 123-127: checks if PASSWDI is SPACES or LOW-VALUES.
 */
export function validatePassword(password: string): string | null {
  if (!password || password.trim() === '') {
    return 'Please enter Password ...';
  }
  return null;
}

/**
 * Authenticates user against security file data.
 * COSGN00C lines 132-260: upper-cases user/password, reads USRSEC file,
 * compares password, routes based on user type.
 */
export function authenticate(
  userId: string,
  password: string,
  secRecord: SecUserData | null
): SignOnResult {
  const upperUserId = userId.toUpperCase();
  const upperPassword = password.toUpperCase();

  // COSGN00C line 247-250: User not found (RESP code 13)
  if (!secRecord) {
    return { success: false, errorMessage: 'User not found. Try again ...' };
  }

  // COSGN00C lines 223-245: Password check
  if (secRecord.usrPwd !== upperPassword) {
    return { success: false, errorMessage: 'Wrong Password. Try again ...' };
  }

  // COSGN00C lines 230-239: Route based on user type
  const targetProgram = secRecord.usrType === 'A' ? 'COADM01C' : 'COMEN01C';
  return { success: true, targetProgram, userType: secRecord.usrType };
}

/**
 * Validates AID key press.
 * COSGN00C lines 85-96: only ENTER and PF3 are valid.
 */
export function validateAidKey(
  key: 'ENTER' | 'PF3' | string
): { valid: boolean; message?: string } {
  switch (key) {
    case 'ENTER':
      return { valid: true };
    case 'PF3':
      return { valid: true, message: 'Thank you for using CardDemo application...' };
    default:
      return { valid: false, message: 'Invalid key pressed. Please see below ...' };
  }
}
