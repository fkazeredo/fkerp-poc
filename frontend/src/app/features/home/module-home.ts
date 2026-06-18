import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NavigationService } from '../../core/navigation/navigation';

/**
 * Reusable home page for a business module (CRM, Vendas, Cadastros). It reads the module id from the route
 * data and renders the module's destinations as tiles, driven by the single navigation config so the sidebar
 * and the homes never drift. Shows an access notice when the module is not visible to the user.
 */
@Component({
  selector: 'app-module-home',
  imports: [RouterLink],
  templateUrl: './module-home.html',
  styleUrls: ['./tiles.css'],
})
export class ModuleHome {
  private readonly route = inject(ActivatedRoute);
  private readonly nav = inject(NavigationService);

  protected readonly module = this.nav.module(this.route.snapshot.data['module']);
}
