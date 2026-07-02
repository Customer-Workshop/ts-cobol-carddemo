import { Body, Controller, HttpCode, Post } from '@nestjs/common';
import { AuthService } from './auth.service';
import { Public } from './decorators';

@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  // POST /auth/signin (legacy COSGN00C sign-on) — REQ-F-082..REQ-F-092.
  @Public()
  @Post('signin')
  @HttpCode(200)
  signIn(@Body() body: unknown): ReturnType<AuthService['signIn']> {
    return this.authService.signIn(body);
  }
}
