import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { bookingReadGuard } from './core/auth/booking-read.guard';
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
        path: 'comercial',
        data: { module: 'comercial' },
        loadComponent: () => import('./features/home/module-home').then((m) => m.ModuleHome),
      },
      {
        path: 'acompanhamento',
        data: { module: 'acompanhamento' },
        loadComponent: () => import('./features/home/module-home').then((m) => m.ModuleHome),
      },
      {
        path: 'cadastros',
        data: { module: 'cadastros' },
        loadComponent: () => import('./features/home/module-home').then((m) => m.ModuleHome),
      },
      {
        path: 'reservas',
        canActivate: [bookingReadGuard],
        loadComponent: () =>
          import('./features/bookings/booking-list/booking-list').then((m) => m.BookingList),
      },
      {
        path: 'pendencias',
        loadComponent: () =>
          import('./features/pendencias/pendencias-hub/pendencias-hub').then((m) => m.PendenciasHub),
      },
      {
        path: 'indicadores',
        loadComponent: () =>
          import('./features/indicadores/indicadores-hub/indicadores-hub').then(
            (m) => m.IndicadoresHub,
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
