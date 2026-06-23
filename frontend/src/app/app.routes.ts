import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { bookingReadGuard } from './core/auth/booking-read.guard';
import { crmReadGuard } from './core/auth/crm-read.guard';
import { opportunityReadGuard } from './core/auth/opportunity-read.guard';
import { orderReadGuard } from './core/auth/order-read.guard';
import { proposalReadGuard } from './core/auth/proposal-read.guard';
import { workflowManageGuard } from './core/auth/workflow-manage.guard';
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
        path: 'reservas/:id',
        canActivate: [bookingReadGuard],
        loadComponent: () =>
          import('./features/bookings/booking-detail/booking-detail').then((m) => m.BookingDetail),
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
      {
        path: 'cadastros/tipos-atividade',
        data: { title: 'Tipos de atividade', path: 'opportunity-activity-types' },
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'cadastros/resultados-atividade',
        data: { title: 'Resultados de atividade', path: 'opportunity-activity-results' },
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'cadastros/motivos-perda-oportunidade',
        data: { title: 'Motivos de perda (oportunidade)', path: 'opportunity-loss-reasons' },
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'cadastros/motivos-rejeicao',
        data: { title: 'Motivos de rejeição', path: 'proposal-rejection-reasons', base: 'sales' },
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'cadastros/motivos-recusa-cliente',
        data: { title: 'Motivos de recusa do cliente', path: 'customer-rejection-reasons', base: 'sales' },
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'cadastros/canais-envio',
        data: { title: 'Canais de envio', path: 'sending-channels', base: 'sales' },
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'cadastros/tipos-item',
        data: { title: 'Tipos de item', path: 'proposal-item-types', base: 'sales' },
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'cadastros/tipos-tentativa',
        data: { title: 'Tipos de tentativa', path: 'booking-attempt-types', base: 'booking' },
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'cadastros/resultados-tentativa',
        data: { title: 'Resultados de tentativa', path: 'booking-attempt-results', base: 'booking' },
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'cadastros/motivos-falha',
        data: { title: 'Motivos de falha', path: 'booking-failure-reasons', base: 'booking' },
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/crm/reference-list/reference-list').then((m) => m.ReferenceList),
      },
      {
        path: 'fluxos',
        canActivate: [workflowManageGuard],
        loadComponent: () =>
          import('./features/workflows/workflow-list/workflow-list').then((m) => m.WorkflowList),
      },
      {
        path: 'fluxos/:code',
        canActivate: [workflowManageGuard],
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/workflows/workflow-editor/workflow-editor').then((m) => m.WorkflowEditor),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
