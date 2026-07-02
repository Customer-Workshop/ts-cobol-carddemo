// Sign-on service, forward-engineered from legacy COSGN00C.
// REQ-F-082..REQ-F-092, REQ-F-375..REQ-F-388 (InteractiveNavigationandMenuControl §9/§33):
// credential validation against the user security store, uppercase normalization,
// distinct user-not-found / wrong-password outcomes, role-based routing (JWT role claim).

import { Injectable, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import * as bcrypt from 'bcryptjs';
import { signInRequestSchema } from '@carddemo/shared';
import { PrismaService } from '../prisma/prisma.service';
import { parseWith } from '../common/zod-validation';
import { serializeUser } from '../common/serializers';
import { JWT_SECRET } from './jwt-auth.guard';

@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly jwtService: JwtService,
  ) {}

  async signIn(body: unknown): Promise<{ token: string; user: ReturnType<typeof serializeUser> }> {
    // REQ-F-083, REQ-F-379, REQ-F-380: user id and password must both be supplied.
    const { userId, password } = parseWith(signInRequestSchema, body);

    // REQ-F-084, REQ-F-381: uppercase normalization before the security-store lookup.
    const normalizedId = userId.toUpperCase();
    const user = await this.prisma.user.findUnique({ where: { id: normalizedId } });
    if (!user) {
      // REQ-F-088, REQ-F-383
      throw new UnauthorizedException('User not found. Try again ...');
    }
    // REQ-F-085, REQ-F-089, REQ-F-382: stored password comparison (bcrypt hash).
    const matches = await bcrypt.compare(password, user.password);
    if (!matches) {
      throw new UnauthorizedException('Wrong Password. Try again ...');
    }
    // REQ-F-086, REQ-F-385..REQ-F-387: session context carries user id + type;
    // the role claim routes ADMIN callers to admin-only functions.
    const token = await this.jwtService.signAsync(
      { sub: user.id, role: user.role },
      { secret: JWT_SECRET, expiresIn: '8h' },
    );
    return { token, user: serializeUser(user) };
  }
}
