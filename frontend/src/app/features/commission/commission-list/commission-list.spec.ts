import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import type { TableLazyLoadEvent } from 'primeng/table';
import { of, throwError } from 'rxjs';
import { CommissionList } from './commission-list';
import { CommissionListItem, CommissionService } from '../../../core/api/commission.service';
import { CommissionRuleService } from '../../../core/api/commission-rule.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

const lazy = (first: number): TableLazyLoadEvent => ({ first });

describe('CommissionList', () => {
  const commissions = { list: vi.fn() };
  const rulesApi = { responsibles: vi.fn(), list: vi.fn() };

  const item: CommissionListItem = {
    id: 'c1',
    beneficiaryUserId: 'u1',
    beneficiaryName: 'vendedor',
    commercialOrderId: 'ord1',
    orderNumber: 7,
    proposalReference: 'Proposta Aurora',
    opportunityReference: 'Aurora',
    amount: 150,
    baseAmount: 3000,
    basisType: 'COMMERCIAL_AMOUNT',
    rulePercentage: 5,
    ruleName: 'Comissão padrão',
    status: 'EXPECTED',
    receivableStatus: null,
    createdAt: '2026-06-20T10:00:00Z',
    eligibleAt: null,
    approvedAt: null,
    paidAt: null,
  };
  const pageOf = (content: CommissionListItem[]) => ({
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true,
  });

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [CommissionList],
      providers: [
        providePrimeNG(),
        provideRouter([]),
        { provide: CommissionService, useValue: commissions },
        { provide: CommissionRuleService, useValue: rulesApi },
      ],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(CommissionList).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(CommissionList);
    fixture.componentInstance.ngOnInit();
    fixture.componentInstance['onLazyLoad'](lazy(0));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    commissions.list.mockReset();
    rulesApi.responsibles.mockReset().mockReturnValue(of([]));
    rulesApi.list.mockReset().mockReturnValue(of([]));
    commissions.list.mockReturnValue(of(pageOf([item])));
  });

  it('loads the commissions on lazy load with the default (operational) filters', () => {
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(commissions.list).toHaveBeenCalledWith(expect.objectContaining({ status: [] }), 0, 20);
    expect(comp['items']()).toHaveLength(1);
    expect(comp['total']()).toBe(1);
  });

  it('loads the beneficiary and rule options on init', () => {
    rulesApi.responsibles.mockReturnValue(of([{ id: 'u1', name: 'vendedor' }]));
    rulesApi.list.mockReturnValue(of([{ id: 'r1', name: 'Comissão padrão' }]));
    const comp = build();
    comp.ngOnInit();
    expect(rulesApi.responsibles).toHaveBeenCalled();
    expect(rulesApi.list).toHaveBeenCalledWith(true);
    expect(comp['beneficiaryOptions']()).toHaveLength(1);
    expect(comp['ruleOptions']()).toHaveLength(1);
  });

  it('applies the status / beneficiary / rule / amount filters and resets to the first page', () => {
    const comp = build();
    comp['firstRow'] = 40;
    comp['status'] = ['ELIGIBLE'];
    comp['beneficiary'] = 'u1';
    comp['rule'] = 'r1';
    comp['amountMin'] = 100;
    comp['applyFilters']();
    expect(comp['firstRow']).toBe(0);
    expect(commissions.list).toHaveBeenCalledWith(
      expect.objectContaining({ status: ['ELIGIBLE'], beneficiary: 'u1', rule: 'r1', amountMin: 100 }),
      0,
      20,
    );
  });

  it('converts the creation / eligibility / payment date filters to ISO', () => {
    const comp = build();
    comp['createdFrom'] = new Date(2026, 6, 1);
    comp['eligibleTo'] = new Date(2026, 5, 30);
    comp['paidFrom'] = new Date(2026, 4, 15);
    comp['applyFilters']();
    expect(commissions.list).toHaveBeenCalledWith(
      expect.objectContaining({ createdFrom: '2026-07-01', eligibleTo: '2026-06-30', paidFrom: '2026-05-15' }),
      0,
      20,
    );
  });

  it('clears every filter and reloads', () => {
    const comp = build();
    comp['status'] = ['PAID'];
    comp['beneficiary'] = 'u1';
    comp['amountMin'] = 5;
    comp['createdFrom'] = new Date(2026, 0, 1);

    comp['clearFilters']();

    expect(comp['status']).toEqual([]);
    expect(comp['beneficiary']).toBeNull();
    expect(comp['amountMin']).toBeNull();
    expect(comp['createdFrom']).toBeNull();
    expect(commissions.list).toHaveBeenCalled();
  });

  it('maps status, basis and receivable labels', () => {
    const comp = build();
    expect(comp['orderCode'](7)).toBe('PC-0007');
    expect(comp['statusLabel']('ELIGIBLE')).toBe('Pendente de aprovação');
    expect(comp['statusSeverity']('ELIGIBLE')).toBe('warn');
    expect(comp['basisLabel']('RECEIVED_AMOUNT')).toBe('Valor recebido');
    expect(comp['receivableLabel']('PAID')).toBe('Paga');
    expect(comp['receivableLabel'](null)).toBe('—');
  });

  it('shows an error message when the load fails', () => {
    commissions.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const comp = build();
    comp['onLazyLoad'](lazy(0));
    expect(comp['error']()).toContain('comissões');
  });

  describe('DOM rendering', () => {
    it('renders the required columns and a commission row', () => {
      commissions.list.mockReturnValue(of(pageOf([item])));
      const el = render();
      expect(el.textContent).toContain('Beneficiário');
      expect(el.textContent).toContain('Pedido');
      expect(el.textContent).toContain('Origem');
      expect(el.textContent).toContain('Valor');
      expect(el.textContent).toContain('Percentual / regra');
      expect(el.textContent).toContain('Status');
      expect(el.textContent).toContain('Conta a receber');
      expect(el.textContent).toContain('Elegível');
      expect(el.textContent).toContain('Aprovada');
      expect(el.textContent).toContain('Paga');
      expect(el.textContent).toContain('PC-0007'); // source order code
      expect(el.textContent).toContain('vendedor'); // beneficiary
      expect(el.textContent).toContain('Comissão padrão'); // rule
      expect(el.textContent).toContain('Prevista'); // EXPECTED status label
      // The beneficiary cell links to the commission detail; the order code links to the order.
      expect(el.querySelector('a[href="/comissoes/c1"]')).not.toBeNull();
      expect(el.querySelector('a[href="/pedidos/ord1"]')).not.toBeNull();
    });

    it('renders an eligible commission as "Pendente de aprovação" with its receivable status', () => {
      commissions.list.mockReturnValue(
        of(pageOf([{ ...item, status: 'ELIGIBLE', receivableStatus: 'PAID', eligibleAt: '2026-06-25T10:00:00Z' }])),
      );
      const el = render();
      expect(el.textContent).toContain('Pendente de aprovação');
      expect(el.textContent).toContain('Paga'); // receivable status label
    });

    it('renders the required filters (status, beneficiary, rule, periods, amount range)', () => {
      const el = render();
      expect(el.textContent).toContain('Status');
      expect(el.textContent).toContain('Beneficiário');
      expect(el.textContent).toContain('Regra');
      expect(el.textContent).toContain('Criada de');
      expect(el.textContent).toContain('Elegível de');
      expect(el.textContent).toContain('Paga de');
      expect(el.textContent).toContain('Valor mín.');
    });

    it('renders the empty state when there are no commissions', () => {
      commissions.list.mockReturnValue(of(pageOf([])));
      expect(render().textContent).toContain('Nenhuma comissão para acompanhar.');
    });
  });
});
