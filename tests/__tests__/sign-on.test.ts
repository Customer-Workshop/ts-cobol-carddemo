/**
 * Unit tests for COSGN00C.cbl - Signon Screen
 * Tests authentication flow, input validation, and user routing.
 */
import {
  validateUserId,
  validatePassword,
  authenticate,
  validateAidKey,
} from '../validators/sign-on';
import { SecUserData } from '../models/records';

describe('COSGN00C - Signon Screen', () => {
  describe('validateUserId', () => {
    it('rejects empty user ID', () => {
      expect(validateUserId('')).toBe('Please enter User ID ...');
    });

    it('rejects whitespace-only user ID', () => {
      expect(validateUserId('        ')).toBe('Please enter User ID ...');
    });

    it('accepts valid user ID', () => {
      expect(validateUserId('USER0001')).toBeNull();
    });

    it('accepts user ID with leading/trailing chars', () => {
      expect(validateUserId('ADMIN01')).toBeNull();
    });
  });

  describe('validatePassword', () => {
    it('rejects empty password', () => {
      expect(validatePassword('')).toBe('Please enter Password ...');
    });

    it('rejects whitespace-only password', () => {
      expect(validatePassword('        ')).toBe('Please enter Password ...');
    });

    it('accepts valid password', () => {
      expect(validatePassword('PASS1234')).toBeNull();
    });
  });

  describe('authenticate', () => {
    const adminUser: SecUserData = {
      usrId: 'ADMIN001',
      usrFname: 'ADMIN',
      usrLname: 'USER',
      usrPwd: 'ADMIN123',
      usrType: 'A',
    };

    const regularUser: SecUserData = {
      usrId: 'USER0001',
      usrFname: 'REGULAR',
      usrLname: 'USER',
      usrPwd: 'USER1234',
      usrType: 'U',
    };

    it('returns user not found when no security record exists', () => {
      const result = authenticate('UNKNOWN', 'PASS', null);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.errorMessage).toBe('User not found. Try again ...');
      }
    });

    it('rejects wrong password', () => {
      const result = authenticate('ADMIN001', 'WRONG', adminUser);
      expect(result.success).toBe(false);
      if (!result.success) {
        expect(result.errorMessage).toBe('Wrong Password. Try again ...');
      }
    });

    it('upper-cases password before comparison', () => {
      const result = authenticate('ADMIN001', 'admin123', adminUser);
      expect(result.success).toBe(true);
    });

    it('routes admin user to COADM01C', () => {
      const result = authenticate('ADMIN001', 'ADMIN123', adminUser);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.targetProgram).toBe('COADM01C');
        expect(result.userType).toBe('A');
      }
    });

    it('routes regular user to COMEN01C', () => {
      const result = authenticate('USER0001', 'USER1234', regularUser);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.targetProgram).toBe('COMEN01C');
        expect(result.userType).toBe('U');
      }
    });

    it('is case-insensitive for user input', () => {
      const result = authenticate('user0001', 'user1234', regularUser);
      expect(result.success).toBe(true);
    });
  });

  describe('validateAidKey', () => {
    it('accepts ENTER key', () => {
      const result = validateAidKey('ENTER');
      expect(result.valid).toBe(true);
    });

    it('accepts PF3 key with thank you message', () => {
      const result = validateAidKey('PF3');
      expect(result.valid).toBe(true);
      expect(result.message).toContain('Thank you');
    });

    it('rejects unknown keys', () => {
      const result = validateAidKey('PF5');
      expect(result.valid).toBe(false);
      expect(result.message).toContain('Invalid key');
    });

    it('rejects PF1', () => {
      const result = validateAidKey('PF1');
      expect(result.valid).toBe(false);
    });
  });
});
