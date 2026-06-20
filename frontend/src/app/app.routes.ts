import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { crmReadGuard } from './core/auth/crm-read.guard';
import { opportunityReadGuard } from './core/auth/opportunity-read.guard';
import { orderReadGuard } from './core/auth/order-read.guard';
import { proposalReadGuard } from './core/auth/proposal-read.guard';
import { unsavedChangesGuard } from './core/guards/unsaved-changes.guard';

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
        path: 'crm',
        data: { module: 'crm' },
        loadComponent: () => import('./features/home/module-home').then((m) => m.ModuleHome),
      },
      {
        path: 'vendas',
        data: { module: 'vendas' },
        loadComponent: () => import('./features/home/module-home').then((m) => m.ModuleHome),
      },
      {
        path: 'cadastros',
        data: { module: 'cadastros' },
        loadComponent: () => import('./features/home/module-home').then((m) => m.ModuleHome),
      },
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
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/leads/lead-create/lead-create').then((m) => m.LeadCreate),
      },
      {
        path: 'leads/:id',
        canActivate: [crmReadGuard],
        canDeactivate: [unsavedChangesGuard],
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
        path: 'oportunidades/indicadores',
        canActivate: [opportunityReadGuard],
        loadComponent: () =>
          import('./features/opportunities/opportunity-indicators/opportunity-indicators').then(
            (m) => m.OpportunityIndicatorsPage,
          ),
      },
      {
        path: 'oportunidades/:id',
        canActivate: [opportunityReadGuard],
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/opportunities/opportunity-detail/opportunity-detail').then(
            (m) => m.OpportunityDetailPage,
          ),
      },
      {
        path: 'propostas',
        canActivate: [proposalReadGuard],
        loadComponent: () =>
          import('./features/proposals/proposal-list/proposal-list').then((m) => m.ProposalList),
      },
      {
        path: 'propostas/:id',
        canActivate: [proposalReadGuard],
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/proposals/proposal-detail/proposal-detail').then(
            (m) => m.ProposalDetailPage,
          ),
      },
      {
        path: 'pedidos',
        canActivate: [orderReadGuard],
        loadComponent: () => import('./features/orders/order-list/order-list').then((m) => m.OrderList),
      },
      {
        path: 'pedidos/:id',
        canActivate: [orderReadGuard],
        loadComponent: () =>
          import('./features/orders/order-detail/order-detail').then((m) => m.OrderDetailPage),
      },
      {
        path: 'cadastros/origens',
        data: { title: 'Origens', path: 'origins' },
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'cadastros/motivos-perda',
        data: { title: 'Motivos de perda', path: 'loss-reasons' },
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'cadastros/tipos-interacao',
        data: { title: 'Tipos de interação', path: 'interaction-types' },
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'cadastros/resultados-interacao',
        data: { title: 'Resultados de interação', path: 'interaction-results' },
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
