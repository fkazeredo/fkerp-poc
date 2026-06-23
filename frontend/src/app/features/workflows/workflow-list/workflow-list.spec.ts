import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { WorkflowList } from './workflow-list';
import { WorkflowService } from '../../../core/api/workflow.service';

describe('WorkflowList', () => {
  const workflows = { list: vi.fn() };
  const router = { navigateByUrl: vi.fn() };

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [WorkflowList],
      providers: [
        providePrimeNG(),
        { provide: WorkflowService, useValue: workflows },
        { provide: Router, useValue: router },
      ],
    });
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(WorkflowList);
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    return { el: fixture.nativeElement as HTMLElement, comp: fixture.componentInstance };
  }

  beforeEach(() => {
    workflows.list.mockReset();
    router.navigateByUrl.mockReset();
    workflows.list.mockReturnValue(
      of([
        { code: 'opportunity', label: 'Oportunidade' },
        { code: 'lead', label: 'Lead' },
      ]),
    );
  });

  it('lists the workflows as tiles', () => {
    const { el } = render();
    const tiles = el.querySelectorAll('.tile');
    expect(tiles).toHaveLength(2);
    expect(el.textContent).toContain('Oportunidade');
    expect(el.textContent).toContain('lead');
  });

  it('navigates to the editor on a tile click', () => {
    const { comp } = render();
    comp['open']('opportunity');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/cadastros/workflows/opportunity');
  });

  it('shows the empty state when there are no workflows', () => {
    workflows.list.mockReturnValue(of([]));
    expect(render().el.textContent).toMatch(/Nenhum workflow/i);
  });

  it('shows a permission message on 403', () => {
    workflows.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    expect(render().el.textContent).toContain('permissão');
  });
});
