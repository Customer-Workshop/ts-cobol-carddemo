import { Body, Controller, Delete, Get, HttpCode, Param, Post, Put, Query } from '@nestjs/common';
import { UserRole } from '@prisma/client';
import { Roles } from '../auth/decorators';
import { UsersService } from './users.service';

// Admin-only user management (legacy COUSR00C/01C/02C/03C).
// REQ-F-097: standard users are denied access to admin functions.
@Controller('users')
@Roles(UserRole.ADMIN)
export class UsersController {
  constructor(private readonly usersService: UsersService) {}

  // GET /users (legacy COUSR00C) — REQ-F-505..REQ-F-517.
  @Get()
  listUsers(
    @Query('page') page?: string,
    @Query('pageSize') pageSize?: string,
  ): ReturnType<UsersService['listUsers']> {
    return this.usersService.listUsers(page, pageSize);
  }

  // POST /users (legacy COUSR01C) — REQ-F-553..REQ-F-561.
  @Post()
  createUser(@Body() body: unknown): ReturnType<UsersService['createUser']> {
    return this.usersService.createUser(body);
  }

  // PUT /users/{userId} (legacy COUSR02C) — REQ-F-523..REQ-F-534, REQ-F-571..REQ-F-590.
  @Put(':userId')
  updateUser(
    @Param('userId') userId: string,
    @Body() body: unknown,
  ): ReturnType<UsersService['updateUser']> {
    return this.usersService.updateUser(userId, body);
  }

  // DELETE /users/{userId} (legacy COUSR03C) — REQ-F-535..REQ-F-547, REQ-F-598..REQ-F-611.
  @Delete(':userId')
  @HttpCode(204)
  deleteUser(@Param('userId') userId: string): ReturnType<UsersService['deleteUser']> {
    return this.usersService.deleteUser(userId);
  }
}
