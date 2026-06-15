import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';

/** Landing page with quick links and a reminder of the keyboard shortcuts. */
@Component({
  selector: 'app-home',
  imports: [ButtonModule, CardModule],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {
  private readonly router = inject(Router);

  protected go(url: string): void {
    this.router.navigateByUrl(url);
  }
}
