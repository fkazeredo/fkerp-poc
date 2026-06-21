import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { ModuleHome } from './module-home';
import { NavigationService, NavModule } from '../../core/navigation/navigation';

describe('ModuleHome', () => {
  const nav = { module: vi.fn() };

  const comercial: NavModule = {
    id: 'comercial',
    title: 'Comercial',
    icon: 'pi pi-briefcase',
    accent: 'leads',
    home: '/comercial',
    desc: 'O funil comercial.',
    items: [
      { label: 'Leads', icon: 'pi pi-list', link: '/leads', exact: false, desc: 'lista', accent: 'leads' },
      { label: 'Pedidos', icon: 'pi pi-shopping-bag', link: '/pedidos', exact: false, desc: 'pedidos', accent: 'sales' },
    ],
    actions: [
      { label: 'Novo lead', icon: 'pi pi-user-plus', link: '/leads/new', exact: false, desc: 'novo', accent: 'new' },
    ],
  };

  function render(moduleId: string, module: NavModule | undefined) {
    nav.module.mockReturnValue(module);
    TestBed.configureTestingModule({
      imports: [ModuleHome],
      providers: [
        provideRouter([]),
        { provide: NavigationService, useValue: nav },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { module: moduleId } } } },
      ],
    });
    const fixture = TestBed.createComponent(ModuleHome);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('renders the module title and a tile per item and action, linking to each destination', () => {
    const el = render('comercial', comercial);
    expect(el.querySelector('h1')?.textContent).toContain('Comercial');
    const links = Array.from(el.querySelectorAll('a.tile')).map((a) => a.getAttribute('href'));
    expect(links).toEqual(['/leads', '/pedidos', '/leads/new']); // items first, then actions
    expect(el.textContent).toContain('Novo lead');
  });

  it('shows a no-access notice when the module is not visible to the user', () => {
    const el = render('reservas', undefined);
    expect(el.querySelectorAll('a.tile')).toHaveLength(0);
    expect(el.querySelector('.notice')?.textContent).toContain('não tem acesso');
  });
});
