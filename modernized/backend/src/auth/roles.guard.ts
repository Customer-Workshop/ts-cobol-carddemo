// Role guard: routes marked @Roles(ADMIN) require an ADMIN JWT.
// REQ-F-097 (InteractiveNavigationandMenuControl): standard users selecting an
// admin-only function are denied access.

import { CanActivate, ExecutionContext, ForbiddenException, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import type { Request } from 'express';
import type { UserRole } from '@prisma/client';
import { AuthenticatedUser, ROLES_KEY } from './decorators';

@Injectable()
export class RolesGuard implements CanActivate {
  constructor(private readonly reflector: Reflector) {}

  canActivate(context: ExecutionContext): boolean {
    const roles = this.reflector.getAllAndOverride<UserRole[] | undefined>(ROLES_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);
    if (!roles || roles.length === 0) return true;
    const request = context.switchToHttp().getRequest<Request & { user?: AuthenticatedUser }>();
    const user = request.user;
    if (!user || !roles.includes(user.role)) {
      throw new ForbiddenException('Caller lacks the required role');
    }
    return true;
  }
}
