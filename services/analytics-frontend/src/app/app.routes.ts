import { Routes } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideFormly } from './builder/formly.provider';

export const routes: Routes = [
  {
    path: 'demo',
    loadComponent: () =>
      import('./demo/demo-page.component').then((m) => m.DemoPageComponent),
  },
  {
    path: 'builder',
    providers: [provideAnimations(), provideFormly()],
    loadComponent: () =>
      import('./builder/builder-page.component').then(
        (m) => m.BuilderPageComponent,
      ),
  },
  { path: '', redirectTo: 'demo', pathMatch: 'full' },
];
