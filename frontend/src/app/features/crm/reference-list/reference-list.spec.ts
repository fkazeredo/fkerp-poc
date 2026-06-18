import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { of } from 'rxjs';
import { ReferenceList } from './reference-list';
import { ReferenceService } from '../../../core/api/reference.service';

(globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

describe('ReferenceList', () => {
  const api = { list: vi.fn(), create: vi.fn(), update: vi.fn(), deactivate: vi.fn() };
  const messages = { add: vi.fn() };

  const item = (over: Partial<Record<string, unknown>> = {}) => ({
    id: 'i1',
    code: 'WEBSITE',
    label: 'Website',
    active: true,
    sortOrder: 1,
    ...over,
  });

  function configure() {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ReferenceList],
      providers: [
        providePrimeNG(),
        ConfirmationService,
        { provide: ReferenceService, useValue: api },
        { provide: MessageService, useValue: messages },
        { provide: ActivatedRoute, useValue: { data: of({ title: 'Origens', path: 'origins' }) } },
      ],
    });
  }

  function build() {
    configure();
    return TestBed.createComponent(ReferenceList).componentInstance;
  }

  function render() {
    configure();
    const fixture = TestBed.createComponent(ReferenceList);
    fixture.detectChanges();
    return { el: fixture.nativeElement as HTMLElement, comp: fixture.componentInstance, fixture };
  }

  beforeEach(() => {
    Object.values(api).forEach((fn) => fn.mockReset());
    messages.add.mockReset();
    api.list.mockReturnValue(of([item()]));
    api.create.mockReturnValue(of(item({ id: 'i2', code: 'TIKTOK', label: 'TikTok' })));
    api.update.mockReturnValue(of(item()));
    api.deactivate.mockReturnValue(of(undefined));
  });

  it('reads the route data and lists the cadastro', () => {
    const comp = build();
    expect(comp['title']()).toBe('Origens');
    expect(api.list).toHaveBeenCalledWith('origins', false);
    expect(comp['items']()).toHaveLength(1);
  });

  it('creates a new record with the form payload then reloads', () => {
    const comp = build();
    comp['openCreate']();
    comp['form'].patchValue({ code: 'TIKTOK', label: 'TikTok', sortOrder: 5 });

    comp['save']();

    expect(api.create).toHaveBeenCalledWith('origins', {
      code: 'TIKTOK',
      label: 'TikTok',
      sortOrder: 5,
    });
    expect(api.list).toHaveBeenCalledTimes(2); // initial + reload
    expect(messages.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
  });

  it('deactivates a record and reloads', () => {
    const comp = build();
    comp['deactivate'](item());
    expect(api.deactivate).toHaveBeenCalledWith('origins', 'i1');
    expect(api.list).toHaveBeenCalledTimes(2);
  });

  it('toggles inactive visibility and re-queries with the flag', () => {
    const comp = build();
    comp['toggleInactive']();
    expect(comp['includeInactive']()).toBe(true);
    expect(api.list).toHaveBeenLastCalledWith('origins', true);
  });

  // Regression: the dialog "Cancelar" must close the dialog without saving.
  it('closes the dialog on cancel without saving', () => {
    const comp = build();
    comp['openCreate']();
    expect(comp['dialogOpen']()).toBe(true);

    comp['closeDialog']();

    expect(comp['dialogOpen']()).toBe(false);
    expect(api.create).not.toHaveBeenCalled();
  });

  // Regression: Esc closes the create/edit dialog (the cadastro "exit" shortcut).
  it('closes the dialog on Escape when there are no changes', () => {
    const comp = build();
    comp['openCreate']();
    expect(comp['dialogOpen']()).toBe(true);

    comp['onEscape']();

    expect(comp['dialogOpen']()).toBe(false);
    expect(api.create).not.toHaveBeenCalled();
  });

  it('ignores Escape when no dialog is open', () => {
    const comp = build();
    expect(comp['dialogOpen']()).toBe(false);

    expect(() => comp['onEscape']()).not.toThrow();
    expect(comp['dialogOpen']()).toBe(false);
  });

  it('warns before discarding on Escape when the form was changed, and keeps the dialog open on reject', async () => {
    const comp = build();
    comp['openCreate']();
    comp['form'].patchValue({ code: 'TIKTOK', label: 'TikTok' });
    comp['form'].markAsDirty();

    const confirmation = TestBed.inject(ConfirmationService);
    const confirm = vi.spyOn(confirmation, 'confirm').mockImplementation((opts) => {
      opts.reject?.(); // user chooses "Continuar editando"
      return confirmation;
    });

    await comp['closeDialog']();

    expect(confirm).toHaveBeenCalled();
    expect(comp['dialogOpen']()).toBe(true); // stays open — nothing discarded
  });

  it('discards and closes on Escape when the user confirms', async () => {
    const comp = build();
    comp['openCreate']();
    comp['form'].patchValue({ code: 'TIKTOK', label: 'TikTok' });
    comp['form'].markAsDirty();

    const confirmation = TestBed.inject(ConfirmationService);
    vi.spyOn(confirmation, 'confirm').mockImplementation((opts) => {
      opts.accept?.(); // user chooses "Descartar"
      return confirmation;
    });

    await comp['closeDialog']();

    expect(comp['dialogOpen']()).toBe(false);
    expect(api.create).not.toHaveBeenCalled();
  });

  describe('DOM rendering', () => {
    it('renders the cadastro title and a row', () => {
      api.list.mockReturnValue(of([item()]));
      const { el } = render();
      expect(el.querySelector('h1')?.textContent).toContain('Origens');
      expect(el.textContent).toContain('WEBSITE'); // code
      expect(el.textContent).toContain('Website'); // label
    });

    it('renders the empty state when the cadastro has no records', () => {
      api.list.mockReturnValue(of([]));
      expect(render().el.textContent).toMatch(/Nenhum/i);
    });
  });
});
