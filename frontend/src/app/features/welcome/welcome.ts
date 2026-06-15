import { Component, signal } from '@angular/core';

@Component({
  selector: 'app-welcome',
  templateUrl: './welcome.html',
  styleUrl: './welcome.css'
})
export class Welcome {
  protected readonly title = signal('FKERP');
}
