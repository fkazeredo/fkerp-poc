import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { NEVER, of, throwError } from 'rxjs';
import { ReceivableDetailPage } from './receivable-detail';
import { ReceivableDetail, ReceivableService } from '../../../core/api/receivable.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('ReceivableDetailPage', () => {
  const receivables = { detail: vi.fn() };
  const router = { navigateByUrl: vi.fn() };

  const sample: ReceivableDetail = {
    id: 'r1',
    commercialOrderId: 'ord1',
    orderNumber: 7,
    proposalId: 'p1',
    opportunityId: 'o1',
    leadId: 'l1',
    customerId: 'c1',
    customerName: 'Maria Silva',
    commercialResponsibleId: 'u1',
    commercialResponsibleName: 'comercial',
    financialResponsibleId: null,
    financialResponsibleName: null,
    totalAmount: 1500,
    dueDate: '2026-07-15',
    paymentNotes: 'Boleto à vista',
    status: 'OPEN',
    createdAt: '2026-06-20T10:00:00Z',
    createdByName: 'financeiro',
  };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ReceivableDetailPage],
      providers: [
        providePrimeNG(),
        { provide: ReceivableService, useValue: receivables },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'r1' } } } },
      ],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(ReceivableDetailPage).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(ReceivableDetailPage);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    receivables.detail.mockReset();
    router.navigateByUrl.mockReset();
    receivables.detail.mockReturnValue(of(sample));
  });

  it('loads the receivable on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(receivables.detail).toHaveBeenCalledWith('r1');
    expect(comp['receivable']()).toEqual(sample);
    expect(comp['loading']()).toBe(false);
  });

  it('maps the status label and severity to pt-BR', () => {
    const comp = build();
    expect(comp['statusLabel']('OPEN')).toBe('Em aberto');
    expect(comp['statusSeverity']('PAID')).toBe('success');
    expect(comp['orderCode'](7)).toBe('PC-0007');
  });

  it('shows a permission message on 403', () => {
    receivables.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('permissão');
  });

  it('shows a not-found message on 404', () => {
    receivables.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('não encontrada');
  });

  it('navigates back to the receivable list', () => {
    const comp = build();
    comp.ngOnInit();
    comp['back']();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/financeiro/contas-a-receber');
  });

  describe('DOM rendering', () => {
    it('renders the loading state while the detail is in flight', () => {
      receivables.detail.mockReturnValue(NEVER);
      expect(render().textContent).toContain('Carregando');
    });

    it('renders the receivable record: value, payer, status and traceable origin links', () => {
      receivables.detail.mockReturnValue(of(sample));
      const el = render();
      expect(el.querySelector('h1')?.textContent).toContain('Conta a receber · PC-0007');
      expect(el.textContent).toContain('Em aberto'); // status
      expect(el.textContent).toContain('Maria Silva'); // payer
      expect(el.textContent).toContain('Boleto à vista'); // payment notes
      expect(el.textContent).toContain('Ver pedido de origem');
      expect(el.textContent).toContain('Ver proposta');
      expect(el.textContent).toContain('Ver lead de origem');
      // The contract carries no Payment/Commission/Invoice labels.
      expect(el.textContent).not.toContain('Comissão');
      expect(el.textContent).not.toContain('Pagamento registrado');
    });

    it('renders the error state with a back button on 403', () => {
      receivables.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
      const el = render();
      expect(el.textContent).toContain('permissão');
      expect(el.textContent).toContain('Voltar');
    });
  });
});
