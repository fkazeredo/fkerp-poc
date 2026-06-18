import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { LeadCreate } from './lead-create';
import { LeadService } from '../../../core/api/lead.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('LeadCreate', () => {
  const leads = { origins: vi.fn(), responsibles: vi.fn(), create: vi.fn() };
  const router = { navigateByUrl: vi.fn() };
  const messages = { add: vi.fn() };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [LeadCreate],
      providers: [
        providePrimeNG(),
        ConfirmationService,
        { provide: LeadService, useValue: leads },
        { provide: Router, useValue: router },
        { provide: MessageService, useValue: messages },
      ],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(LeadCreate).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(LeadCreate);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    leads.origins.mockReset();
    leads.responsibles.mockReset();
    leads.create.mockReset();
    router.navigateByUrl.mockReset();
    messages.add.mockReset();
    leads.origins.mockReturnValue(of([{ id: 'o1', code: 'WEBSITE', label: 'Website' }]));
    leads.responsibles.mockReturnValue(of([{ id: 'u1', name: 'comercial' }]));
  });

  it('loads the origins and responsibles for the dropdowns on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(leads.origins).toHaveBeenCalled();
    expect(leads.responsibles).toHaveBeenCalled();
    expect(comp['origins']()).toHaveLength(1);
    expect(comp['origins']()[0].label).toBe('Website');
    expect(comp['responsibles']()).toHaveLength(1);
  });

  it('sends the selected responsible person id in the payload', () => {
    leads.create.mockReturnValue(of({ id: 'l1', name: 'Maria', status: 'NEW' }));
    const comp = build();
    comp['form'].patchValue({
      name: 'Maria',
      originId: 'o1',
      phone: '11999999999',
      responsibleId: 'u1',
    });

    comp['submit']();

    expect(leads.create.mock.calls[0][0].responsiblePersonId).toBe('u1');
  });

  it('does not submit when required fields are missing', () => {
    const comp = build();
    comp['submit']();
    expect(leads.create).not.toHaveBeenCalled();
  });

  it('sends a normalized payload, toasts success and navigates home', () => {
    leads.create.mockReturnValue(of({ id: 'l1', name: 'Maria', status: 'NEW' }));
    const comp = build();
    comp['form'].patchValue({ name: 'Maria', originId: 'o1', phone: '11999999999' });

    comp['submit']();

    expect(leads.create).toHaveBeenCalledTimes(1);
    const payload = leads.create.mock.calls[0][0];
    expect(payload).toMatchObject({ name: 'Maria', originId: 'o1', phone: '11999999999' });
    expect(payload.email).toBeNull();
    expect(payload.whatsapp).toBeNull();
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('maps backend field errors (400) onto the form fields', () => {
    leads.create.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              code: 'validation.failed',
              message: 'Falha',
              fields: { name: 'Nome é obrigatório' },
            },
          }),
      ),
    );
    const comp = build();
    comp['form'].patchValue({ name: 'X', originId: 'o1', phone: '11999999999' });

    comp['submit']();

    expect(comp['fieldError']('name')).toBe('Nome é obrigatório');
    expect(comp['formError']()).toBe('Falha');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  // Regression: "Cancelar" must leave the form (navigate home), not silently reset — clicking it
  // on an empty form used to do nothing visible, so it looked broken.
  it('navigates home on cancel and does not create', () => {
    leads.create.mockReturnValue(of({ id: 'l1', name: 'Maria', status: 'NEW' }));
    const comp = build();
    comp['form'].patchValue({ name: 'Maria', originId: 'o1', phone: '11999999999' });

    comp['cancel']();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
    expect(leads.create).not.toHaveBeenCalled();
  });

  // The Esc key must exit a full-page form too (not only modals), via the same guard as Cancel.
  it('cancels the screen on Escape (navigates home) when nothing is open and the form is clean', () => {
    const comp = build();

    comp['onEscape'](new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(router.navigateByUrl).toHaveBeenCalledWith('/');
  });

  it('warns before leaving on Escape when the form was changed, and stays on reject', async () => {
    const comp = build();
    comp['form'].patchValue({ name: 'Rascunho' });
    comp['form'].markAsDirty();
    const confirmation = TestBed.inject(ConfirmationService);
    const confirm = vi.spyOn(confirmation, 'confirm').mockImplementation((opts) => {
      opts.reject?.(); // "Continuar editando"
      return confirmation;
    });

    await comp['cancel']();

    expect(confirm).toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('does not leave on Escape while a dropdown overlay is open', () => {
    const comp = build();
    const overlay = document.createElement('div');
    overlay.className = 'p-select-overlay';
    document.body.appendChild(overlay);
    try {
      comp['onEscape'](new KeyboardEvent('keydown', { key: 'Escape' }));
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    } finally {
      overlay.remove();
    }
  });

  it('ignores Escape already consumed by another control (defaultPrevented)', () => {
    const comp = build();
    const event = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true });
    event.preventDefault();

    comp['onEscape'](event);

    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  describe('DOM rendering', () => {
    it('renders the lead form with its required fields and the submit button', () => {
      const el = render();
      expect(el.querySelector('h1')?.textContent).toContain('Novo lead');
      expect(el.querySelector('#name')).not.toBeNull();
      expect(el.querySelector('#phone')).not.toBeNull();
      expect(el.textContent).toContain('Salvar lead');
      expect(el.textContent).toContain('Cancelar');
    });

    it('shows a server-side field error in the DOM', () => {
      configure();
      const fixture = TestBed.createComponent(LeadCreate);
      fixture.componentInstance.ngOnInit();
      fixture.componentInstance['fieldErrors'].set({ phone: 'Telefone inválido' });
      fixture.detectChanges();
      expect((fixture.nativeElement as HTMLElement).textContent).toContain('Telefone inválido');
    });
  });
});
