import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { EchartsRendererComponent } from '../widgets/echarts-renderer.component';
import { SampleDataService } from '../widgets/sample-data.service';

@Component({
  selector: 'app-demo-page',
  standalone: true,
  imports: [CommonModule, EchartsRendererComponent],
  templateUrl: './demo-page.component.html',
  styles: [
    `
      :host {
        display: block;
        padding: 24px;
        max-width: 1200px;
        margin: 0 auto;
      }
      h1 {
        font-size: 1.5rem;
        margin-bottom: 4px;
      }
      h2 {
        font-size: 1.1rem;
        margin: 24px 0 8px 0;
        color: #444;
      }
      .subtitle {
        color: #777;
        margin-top: 0;
      }
      .stack > * + * {
        margin-top: 24px;
      }
    `,
  ],
})
export class DemoPageComponent {
  private readonly sample = inject(SampleDataService);

  readonly barGroupedWorkflow = this.sample.getBarGroupedWorkflow();
  readonly halfDonutWorkflow = this.sample.getHalfDonutWorkflow();
  readonly comboDualAxisWorkflow = this.sample.getComboDualAxisWorkflow();

  readonly barGroupedData = this.sample.getBarGroupedDataset();
  readonly halfDonutData = this.sample.getHalfDonutDataset();
  readonly comboDualAxisData = this.sample.getComboDualAxisDataset();
}
