import { Component } from '@angular/core';
import { Welcome } from './features/welcome/welcome';

@Component({
  selector: 'app-root',
  imports: [Welcome],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {}
