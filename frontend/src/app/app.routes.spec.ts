import { routes } from './app.routes';
import { SignonComponent } from './features/signon/signon.component';
import { MenuComponent } from './features/menu/menu.component';
import { AccountViewComponent } from './features/account-view/account-view.component';

describe('routes', () => {
  const find = (path: string) => routes.find((r) => r.path === path);

  it('exposes the three business-name placeholders', () => {
    expect(find('signon')?.component).toBe(SignonComponent);
    expect(find('menu')?.component).toBe(MenuComponent);
    expect(find('accounts/view')?.component).toBe(AccountViewComponent);
  });

  it('redirects the root and unknown paths to /signon', () => {
    expect(find('')?.redirectTo).toBe('signon');
    expect(find('**')?.redirectTo).toBe('signon');
  });
});
