import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { crmReadGuard } from './core/auth/crm-read.guard';
import { opportunityReadGuard } from './core/auth/opportunity-read.guard';

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
        path: 'pendencias',
        canActivate: [crmReadGuard],
        loadComponent: () =>
          import('./features/leads/lead-pending/lead-pending').then((m) => m.LeadPending),
      },
      {
        path: 'indicadores',
        canActivate: [crmReadGuard],
        loadComponent: () =>
          import('./features/leads/lead-indicators/lead-indicators').then(
            (m) => m.LeadIndicatorsPage,
          ),
      },
      {
        path: 'leads',
        canActivate: [crmReadGuard],
        loadComponent: () => import('./features/leads/lead-list/lead-list').then((m) => m.LeadList),
      },
      {
        path: 'leads/new',
        canActivate: [crmReadGuard],
        loadComponent: () =>
          import('./features/leads/lead-create/lead-create').then((m) => m.LeadCreate),
      },
      {
        path: 'leads/:id',
        canActivate: [crmReadGuard],
        loadComponent: () =>
          import('./features/leads/lead-detail/lead-detail').then((m) => m.LeadDetailPage),
      },
      {
        path: 'oportunidades',
        canActivate: [opportunityReadGuard],
        loadComponent: () =>
          import('./features/opportunities/opportunity-list/opportunity-list').then(
            (m) => m.OpportunityList,
          ),
      },
      {
        path: 'oportunidades/pendencias',
        canActivate: [opportunityReadGuard],
        loadComponent: () =>
          import('./features/opportunities/opportunity-pending/opportunity-pending').then(
            (m) => m.OpportunityPending,
          ),
      },
      {
        path: 'oportunidades/:id',
        canActivate: [opportunityReadGuard],
        loadComponent: () =>
          import('./features/opportunities/opportunity-detail/opportunity-detail').then(
            (m) => m.OpportunityDetailPage,
          ),
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
