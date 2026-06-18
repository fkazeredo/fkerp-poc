import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NavigationService } from '../../core/navigation/navigation';

/** System landing page: a card per business module the user can access, leading to each module's home. */
@Component({
  selector: 'app-home',
  imports: [RouterLink],
  templateUrl: './home.html',
  styleUrls: ['./tiles.css'],
})
export class Home {
  private readonly nav = inject(NavigationService);

  protected get modules() {
    return this.nav.modules();
  }
}
