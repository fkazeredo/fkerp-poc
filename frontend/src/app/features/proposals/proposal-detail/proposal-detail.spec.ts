import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { ProposalDetailPage } from './proposal-detail';
import { ProposalDetail, ProposalService } from '../../../core/api/proposal.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('ProposalDetailPage', () => {
  const proposals = { detail: vi.fn() };
  const router = { navigateByUrl: vi.fn() };

  const sample: ProposalDetail = {
    id: 'p1',
    opportunityId: 'o1',
    leadId: 'l1',
    status: 'DRAFT',
    responsibleId: 'u1',
    responsibleName: 'comercial',
    unassigned: false,
    title: 'Proposta corporativa',
    notes: 'detalhes',
    validUntil: '2026-12-31',
    commercialTerms: 'termos',
    createdAt: '2026-06-17T10:00:00Z',
    updatedAt: '2026-06-17T10:00:00Z',
    sourceOpportunity: { id: 'o1', name: 'Aurora', stage: 'READY_FOR_PROPOSAL' },
  };

  function build() {
    TestBed.configureTestingModule({
      imports: [ProposalDetailPage],
      providers: [
        providePrimeNG(),
        { provide: ProposalService, useValue: proposals },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'p1' } } } },
      ],
    });
    return TestBed.createComponent(ProposalDetailPage).componentInstance;
  }

  beforeEach(() => {
    proposals.detail.mockReset();
    proposals.detail.mockReturnValue(of(sample));
  });

  it('loads the proposal on init', () => {
    const comp = build();
    comp.ngOnInit();
    expect(proposals.detail).toHaveBeenCalledWith('p1');
    expect(comp['proposal']()).toEqual(sample);
    expect(comp['loading']()).toBe(false);
  });

  it('maps the status label and severity to pt-BR', () => {
    const comp = build();
    expect(comp['statusLabel']('DRAFT')).toBe('Rascunho');
    expect(comp['statusLabel']('ACCEPTED')).toBe('Aceita');
    expect(comp['statusSeverity']('DRAFT')).toBe('secondary');
    expect(comp['stageLabel']('READY_FOR_PROPOSAL')).toBe('Pronta p/ proposta');
  });

  it('shows a permission message on 403', () => {
    proposals.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('permissão');
  });

  it('shows a not-found message on 404', () => {
    proposals.detail.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    const comp = build();
    comp.ngOnInit();
    expect(comp['error']()).toContain('não encontrada');
  });
});
