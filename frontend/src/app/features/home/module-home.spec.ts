import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { ModuleHome } from './module-home';
import { NavigationService, NavModule } from '../../core/navigation/navigation';

describe('ModuleHome', () => {
  const nav = { module: vi.fn() };

  const crm: NavModule = {
    id: 'crm',
    title: 'Comercial / CRM',
    icon: 'pi pi-briefcase',
    accent: 'leads',
    home: '/crm',
    desc: 'Leads e oportunidades.',
    items: [
      { label: 'Leads', icon: 'pi pi-list', link: '/leads', exact: false, desc: 'lista', accent: 'leads' },
      { label: 'Indicadores', icon: 'pi pi-chart-bar', link: '/indicadores', exact: true, desc: 'kpi', accent: 'indicators' },
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
    const el = render('crm', crm);
    expect(el.querySelector('h1')?.textContent).toContain('Comercial / CRM');
    const links = Array.from(el.querySelectorAll('a.tile')).map((a) => a.getAttribute('href'));
    expect(links).toEqual(['/leads', '/indicadores', '/leads/new']); // items first, then actions
    expect(el.textContent).toContain('Novo lead');
  });

  it('shows a no-access notice when the module is not visible to the user', () => {
    const el = render('vendas', undefined);
    expect(el.querySelectorAll('a.tile')).toHaveLength(0);
    expect(el.querySelector('.notice')?.textContent).toContain('não tem acesso');
  });
});
