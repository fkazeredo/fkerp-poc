import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./core/layout/shell').then((m) => m.Shell),
    children: [
      { path: '', loadComponent: () => import('./features/home/home').then((m) => m.Home) },
      {
        path: 'leads',
        loadComponent: () => import('./features/leads/lead-list/lead-list').then((m) => m.LeadList),
      },
      {
        path: 'leads/new',
        loadComponent: () =>
          import('./features/leads/lead-create/lead-create').then((m) => m.LeadCreate),
      },
      {
        path: 'leads/:id',
        loadComponent: () =>
          import('./features/leads/lead-detail/lead-detail').then((m) => m.LeadDetailPage),
      },
      {
        path: 'cadastros/origens',
        data: { title: 'Origens', path: 'origins' },
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'cadastros/motivos-perda',
        data: { title: 'Motivos de perda', path: 'loss-reasons' },
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'cadastros/tipos-interacao',
        data: { title: 'Tipos de interação', path: 'interaction-types' },
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'cadastros/resultados-interacao',
        data: { title: 'Resultados de interação', path: 'interaction-results' },
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
