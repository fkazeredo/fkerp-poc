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

  function build() {
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
    return TestBed.createComponent(LeadCreate).componentInstance;
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
});
