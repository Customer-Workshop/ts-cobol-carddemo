// Admin user management, forward-engineered from legacy COUSR00C / COUSR01C /
// COUSR02C / COUSR03C (docs/spec/InteractiveNavigationandMenuControl §44-§53).

import { ConflictException, Injectable, NotFoundException } from '@nestjs/common';
import type { Prisma } from '@prisma/client';
import * as bcrypt from 'bcryptjs';
import type { z } from 'zod';
import { userCreateSchema, userIdSchema, userUpdateSchema } from '@carddemo/shared';
import { PrismaService } from '../prisma/prisma.service';
import { badRequest, parseWith } from '../common/zod-validation';
import { PageResult, pageResult, parsePageParams } from '../common/pagination';
import { serializeUser } from '../common/serializers';

@Injectable()
export class UsersService {
  constructor(private readonly prisma: PrismaService) {}

  // REQ-F-505..REQ-F-517: paginated browse of the user security store in key order.
  async listUsers(page?: string, pageSize?: string): Promise<PageResult<ReturnType<typeof serializeUser>>> {
    const params = parsePageParams(page, pageSize);
    const [totalItems, rows] = await this.prisma.$transaction([
      this.prisma.user.count(),
      this.prisma.user.findMany({ orderBy: { id: 'asc' }, skip: params.skip, take: params.take }),
    ]);
    return pageResult(params, totalItems, rows.map(serializeUser));
  }

  // REQ-F-553..REQ-F-561: user registration — required fields validated, record
  // written to the user security store, duplicates rejected.
  async createUser(body: unknown): Promise<ReturnType<typeof serializeUser>> {
    const input = parseWith(userCreateSchema, body);
    const id = input.id.toUpperCase();
    const existing = await this.prisma.user.findUnique({ where: { id } });
    if (existing) {
      // REQ-F-560: duplicate user id.
      throw new ConflictException('User ID already exist...');
    }
    const created = await this.prisma.user.create({
      data: {
        id,
        firstName: input.firstName,
        lastName: input.lastName,
        password: await bcrypt.hash(input.password, 10),
        role: input.role,
      },
    });
    return serializeUser(created);
  }

  // REQ-F-525..REQ-F-532, REQ-F-571..REQ-F-589: lookup, field comparison
  // (change detection), and rewrite of the user security record.
  async updateUser(userId: string, body: unknown): Promise<ReturnType<typeof serializeUser>> {
    const id = this.validateUserId(userId);
    const input: z.infer<typeof userUpdateSchema> = parseWith(userUpdateSchema, body);
    const user = await this.prisma.user.findUnique({ where: { id } });
    if (!user) {
      // REQ-F-528, REQ-F-577
      throw new NotFoundException('User ID NOT found...');
    }

    const data: Prisma.UserUpdateInput = {};
    if (input.firstName !== undefined && input.firstName !== user.firstName) data.firstName = input.firstName;
    if (input.lastName !== undefined && input.lastName !== user.lastName) data.lastName = input.lastName;
    if (input.role !== undefined && input.role !== user.role) data.role = input.role;
    if (input.password !== undefined && !(await bcrypt.compare(input.password, user.password))) {
      data.password = await bcrypt.hash(input.password, 10);
    }
    if (Object.keys(data).length === 0) {
      // REQ-F-532, REQ-F-589: nothing modified — no write.
      badRequest('Please modify to update ...');
    }
    const updated = await this.prisma.user.update({ where: { id }, data });
    // REQ-F-586: success outcome for the updated user id.
    return serializeUser(updated);
  }

  // REQ-F-598..REQ-F-607: lookup then delete of the user security record.
  async deleteUser(userId: string): Promise<void> {
    const id = this.validateUserId(userId);
    const user = await this.prisma.user.findUnique({ where: { id } });
    if (!user) {
      // REQ-F-606
      throw new NotFoundException('User ID NOT found...');
    }
    await this.prisma.user.delete({ where: { id } });
  }

  // REQ-F-525, REQ-F-574, REQ-F-601: user id must be supplied and well-formed.
  private validateUserId(userId: string): string {
    const parsed = userIdSchema.safeParse(userId);
    if (!parsed.success) {
      badRequest('User ID can NOT be empty...', [
        { field: 'userId', message: 'must be 1-9 characters' },
      ]);
    }
    return userId.toUpperCase();
  }
}
