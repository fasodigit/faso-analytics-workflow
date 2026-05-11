import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'demo',
    loadComponent: () =>
      import('./demo/demo-page.component').then((m) => m.DemoPageComponent),
  },
  { path: '', redirectTo: 'demo', pathMatch: 'full' },
];
