import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { CommissionRuleList } from './commission-rule-list';
import {
  CommissionRuleDetail,
  CommissionRuleListItem,
  CommissionRuleService,
} from '../../../core/api/commission-rule.service';
import { UnsavedChangesService } from '../../../core/forms/unsaved-changes.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('CommissionRuleList', () => {
  const rules = {
    list: vi.fn(),
    detail: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    activate: vi.fn(),
    deactivate: vi.fn(),
    responsibles: vi.fn(),
  };
  const unsaved = { set: vi.fn(), confirmDiscard: vi.fn(() => Promise.resolve(true)) };

  const sample: CommissionRuleListItem = {
    id: 'r1',
    name: 'Padrão vendedores',
    percentage: 5,
    targetType: 'SELLER',
    targetUserId: null,
    targetUserName: null,
    active: true,
    startDate: '2026-01-01',
    endDate: null,
  };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [CommissionRuleList],
      providers: [
        providePrimeNG(),
        { provide: CommissionRuleService, useValue: rules },
        { provide: MessageService, useValue: { add: vi.fn() } },
        { provide: ConfirmationService, useValue: { confirm: vi.fn() } },
        { provide: UnsavedChangesService, useValue: unsaved },
      ],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(CommissionRuleList).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(CommissionRuleList);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    Object.values(rules).forEach((fn) => fn.mockReset());
    unsaved.set.mockReset();
    unsaved.confirmDiscard.mockReset().mockResolvedValue(true);
    rules.list.mockReturnValue(of([sample]));
    rules.responsibles.mockReturnValue(of([{ id: 'u2', name: 'vendedor' }]));
    rules.detail.mockReturnValue(of({ ...sample, notes: null, createdAt: '2026-01-01T00:00:00Z' }));
    rules.create.mockReturnValue(of({ id: 'r9', active: true }));
    rules.update.mockReturnValue(of({} as CommissionRuleDetail));
    rules.activate.mockReturnValue(of({} as CommissionRuleDetail));
    rules.deactivate.mockReturnValue(of({} as CommissionRuleDetail));
  });

  it('loads rules and the responsibles lookup on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(rules.list).toHaveBeenCalledWith(false);
    expect(rules.responsibles).toHaveBeenCalled();
    expect(comp['items']()).toHaveLength(1);
  });

  it('toggles including inactive and reloads', () => {
    const comp = build();
    comp.ngOnInit();
    comp['toggleInactive']();
    expect(comp['includeInactive']()).toBe(true);
    expect(rules.list).toHaveBeenLastCalledWith(true);
  });

  it('opens the create dialog with sensible defaults', () => {
    const comp = build();
    comp.ngOnInit();
    comp['openCreate']();
    expect(comp['dialogOpen']()).toBe(true);
    expect(comp['editingId']()).toBeNull();
    expect(comp['form'].controls.targetType.value).toBe('SELLER');
    expect(comp['form'].controls.startDate.value).toBeInstanceOf(Date);
    expect(comp['form'].invalid).toBe(true); // name + percentage still required
  });

  it('does not submit an invalid form', () => {
    const comp = build();
    comp.ngOnInit();
    comp['openCreate']();
    comp['submit']();
    expect(rules.create).not.toHaveBeenCalled();
  });

  it('creates a rule and refreshes the list', () => {
    const comp = build();
    comp.ngOnInit();
    comp['openCreate']();
    comp['form'].patchValue({ name: 'Nova', percentage: 7 });
    comp['submit']();
    expect(rules.create).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Nova', percentage: 7, targetType: 'SELLER' }),
    );
    expect(comp['dialogOpen']()).toBe(false);
    expect(rules.list).toHaveBeenCalledTimes(2); // init + after create
  });

  it('edits a rule: loads detail, prefills and updates', () => {
    const comp = build();
    comp.ngOnInit();
    comp['openEdit'](sample);
    expect(rules.detail).toHaveBeenCalledWith('r1');
    expect(comp['form'].controls.name.value).toBe('Padrão vendedores');
    comp['form'].patchValue({ percentage: 9 });
    comp['submit']();
    expect(rules.update).toHaveBeenCalledWith('r1', expect.objectContaining({ percentage: 9 }));
  });

  it('surfaces a 422 above-limit error from the backend', () => {
    rules.create.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: {
              code: 'commission.rule.percentage-above-limit',
              message: 'O percentual de comissão excede o limite seguro de 50%',
            },
          }),
      ),
    );
    const comp = build();
    comp.ngOnInit();
    comp['openCreate']();
    comp['form'].patchValue({ name: 'Alta', percentage: 60 });
    comp['submit']();
    expect(comp['formError']()).toContain('limite seguro');
    expect(comp['dialogOpen']()).toBe(true);
  });

  it('activates an inactive rule and deactivates an active one', () => {
    const comp = build();
    comp.ngOnInit();
    comp['toggleActive'](sample); // active → deactivate
    expect(rules.deactivate).toHaveBeenCalledWith('r1');
    comp['toggleActive']({ ...sample, active: false });
    expect(rules.activate).toHaveBeenCalledWith('r1');
  });

  it('flags unsaved changes when the dialog is open with a dirty form', () => {
    const comp = build();
    comp.ngOnInit();
    comp['openCreate']();
    expect(comp.hasUnsavedChanges()).toBe(false);
    comp['form'].markAsDirty();
    expect(comp.hasUnsavedChanges()).toBe(true);
  });

  describe('DOM', () => {
    it('renders the rules table with the percentage and target label', () => {
      const el = render();
      expect(el.querySelector('h1')?.textContent).toContain('Regras de comissão');
      expect(el.textContent).toContain('Padrão vendedores');
      expect(el.textContent).toContain('5%');
      expect(el.textContent).toContain('Vendedor');
      expect(el.textContent).toContain('Nova regra');
    });

    it('shows the permission error on 403', () => {
      rules.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
      const el = render();
      expect(el.textContent).toContain('permissão');
    });

    it('renders the empty state when there are no rules', () => {
      rules.list.mockReturnValue(of([]));
      const el = render();
      expect(el.textContent).toContain('Nenhuma regra de comissão cadastrada.');
    });
  });
});
