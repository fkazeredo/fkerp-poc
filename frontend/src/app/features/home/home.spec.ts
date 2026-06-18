import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Home } from './home';
import { NavigationService, NavModule } from '../../core/navigation/navigation';

describe('Home (system landing)', () => {
  const nav = { modules: vi.fn() };

  const mod = (id: string, title: string, home: string): NavModule => ({
    id,
    title,
    icon: 'pi pi-briefcase',
    accent: 'leads',
    home,
    desc: `${title} desc`,
    items: [],
    actions: [],
  });

  function render(modules: NavModule[]) {
    nav.modules.mockReturnValue(modules);
    TestBed.configureTestingModule({
      imports: [Home],
      providers: [provideRouter([]), { provide: NavigationService, useValue: nav }],
    });
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('renders a card per accessible module, linking to each module home', () => {
    const el = render([mod('crm', 'Comercial / CRM', '/crm'), mod('vendas', 'Vendas', '/vendas')]);
    const tiles = el.querySelectorAll('.tile');
    expect(tiles).toHaveLength(2);
    expect(el.textContent).toContain('Comercial / CRM');
    expect(el.textContent).toContain('Vendas');
    const links = Array.from(el.querySelectorAll('a.tile')).map((a) => a.getAttribute('href'));
    expect(links).toEqual(['/crm', '/vendas']);
  });

  it('shows a no-access notice when the user can reach no module', () => {
    const el = render([]);
    expect(el.querySelectorAll('.tile')).toHaveLength(0);
    expect(el.querySelector('.notice')?.textContent).toContain('não tem acesso');
  });
});
