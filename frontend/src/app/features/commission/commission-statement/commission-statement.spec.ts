import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { CommissionStatementPage } from './commission-statement';
import { CommissionService, CommissionStatement } from '../../../core/api/commission.service';
import { AuthService } from '../../../core/auth/auth.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('CommissionStatementPage', () => {
  const commissions = { statement: vi.fn(), responsibles: vi.fn() };
  const auth = { canSeeAllCommissions: vi.fn(), userId: vi.fn() };

  const sample: CommissionStatement = {
    beneficiaryId: 'u1',
    beneficiaryName: 'vendedor',
    periodFrom: null,
    periodTo: null,
    entries: [
      {
        id: 'c1',
        beneficiaryUserId: 'u1',
        beneficiaryName: 'vendedor',
        commercialOrderId: 'ord1',
        orderNumber: 7,
        proposalReference: 'Proposta Aurora',
        opportunityReference: 'Aurora',
        amount: 25,
        baseAmount: 500,
        basisType: 'COMMERCIAL_AMOUNT',
        rulePercentage: 5,
        ruleName: 'Comissão padrão',
        status: 'PAID',
        receivableStatus: 'PAID',
        createdAt: '2026-06-20T10:00:00Z',
        eligibleAt: '2026-06-21T10:00:00Z',
        approvedAt: '2026-06-22T10:00:00Z',
        paidAt: '2026-06-23T10:00:00Z',
      },
    ],
    totals: {
      totalExpected: 0,
      totalEligible: 0,
      totalApproved: 0,
      totalPaid: 25,
      countExpected: 0,
      countEligible: 0,
      countApproved: 0,
      countPaid: 1,
    },
  };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [CommissionStatementPage],
      providers: [
        providePrimeNG(),
        provideRouter([]),
        { provide: CommissionService, useValue: commissions },
        { provide: AuthService, useValue: auth },
      ],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(CommissionStatementPage).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(CommissionStatementPage);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    commissions.statement.mockReset().mockReturnValue(of(sample));
    commissions.responsibles.mockReset().mockReturnValue(of([{ id: 'u1', name: 'vendedor' }]));
    auth.canSeeAllCommissions.mockReset().mockReturnValue(false);
    auth.userId.mockReset().mockReturnValue('u1');
  });

  it('loads the statement for the current user on init (own tier)', () => {
    const comp = build();
    comp.ngOnInit();
    expect(comp['beneficiary']).toBe('u1');
    expect(commissions.statement).toHaveBeenCalledWith('u1', null, null, false);
    expect(comp['statement']()).toEqual(sample);
  });

  it('does not load the responsibles list for an own-tier user', () => {
    const comp = build();
    comp.ngOnInit();
    expect(commissions.responsibles).not.toHaveBeenCalled();
    expect(comp['canChooseBeneficiary']()).toBe(false);
  });

  it('loads the responsibles list for a manager (all tier)', () => {
    auth.canSeeAllCommissions.mockReturnValue(true);
    const comp = build();
    comp.ngOnInit();
    expect(commissions.responsibles).toHaveBeenCalled();
    expect(comp['canChooseBeneficiary']()).toBe(true);
  });

  it('reloads with the chosen period and includeVoided on apply', () => {
    const comp = build();
    comp.ngOnInit();
    comp['beneficiary'] = 'u2';
    comp['from'] = new Date('2026-06-01T00:00:00');
    comp['includeVoided'] = true;
    comp['apply']();
    expect(commissions.statement).toHaveBeenLastCalledWith('u2', '2026-06-01', null, true);
  });

  it('shows a self-only message on 403', () => {
    commissions.statement.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('próprio extrato');
    expect(comp['statement']()).toBeNull();
  });

  describe('DOM rendering', () => {
    it('renders the totals and the entries table', () => {
      const el = render();
      expect(el.querySelector('h1')?.textContent).toContain('Extrato de comissões');
      expect(el.textContent).toContain('vendedor'); // beneficiary
      expect(el.textContent).toContain('Paga'); // total label + status
      expect(el.textContent).toContain('PC-0007'); // entry order code
    });

    it('renders the empty state when there are no entries', () => {
      commissions.statement.mockReturnValue(
        of({ ...sample, entries: [], totals: { ...sample.totals, totalPaid: 0, countPaid: 0 } }),
      );
      const el = render();
      expect(el.textContent).toContain('Nenhuma comissão no período');
    });
  });
});
