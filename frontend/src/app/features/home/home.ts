import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Landing page: quick entry points and a reminder of the keyboard shortcuts. */
@Component({
  selector: 'app-home',
  imports: [RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {}
