import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { WidgetHostComponent } from '../widgets/widget-host.component';
import { SampleDataService } from '../widgets/sample-data.service';

@Component({
  selector: 'app-demo-page',
  standalone: true,
  imports: [CommonModule, WidgetHostComponent],
  templateUrl: './demo-page.component.html',
  styles: [
    `
      :host {
        display: block;
        padding: 24px;
        max-width: 1400px;
        margin: 0 auto;
      }
      h1 { font-size: 1.6rem; margin-bottom: 4px; }
      h2 {
        font-size: 1.05rem;
        margin: 20px 0 8px 0;
        color: #444;
      }
      .subtitle { color: #777; margin-top: 0; }
      .section-header {
        font-size: 1.2rem;
        font-weight: 600;
        color: var(--polarity-neutral, #7b61ff);
        margin: 36px 0 4px 0;
        padding-bottom: 6px;
        border-bottom: 2px solid var(--polarity-neutral, #7b61ff);
      }
      .grid-2 {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 24px;
      }
      .grid-3 {
        display: grid;
        grid-template-columns: 1fr 1fr 1fr;
        gap: 16px;
      }
      .stack > * + * { margin-top: 12px; }
      @media (max-width: 1000px) {
        .grid-2, .grid-3 { grid-template-columns: 1fr; }
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

  readonly barVerticalWorkflow = this.sample.getBarVerticalWorkflow();
  readonly barVerticalData = this.sample.getBarVerticalDataset();
  readonly barHorizontalWorkflow = this.sample.getBarHorizontalWorkflow();
  readonly barHorizontalData = this.sample.getBarHorizontalDataset();
  readonly barStackedWorkflow = this.sample.getBarStackedWorkflow();
  readonly barStackedData = this.sample.getBarStackedDataset();
  readonly bar100PctWorkflow = this.sample.getBar100PctWorkflow();
  readonly bar100PctData = this.sample.getBar100PctDataset();
  readonly pieWorkflow = this.sample.getPieWorkflow();
  readonly pieData = this.sample.getPieDataset();
  readonly donutWorkflow = this.sample.getDonutWorkflow();
  readonly donutData = this.sample.getDonutDataset();
  readonly lineWorkflow = this.sample.getLineWorkflow();
  readonly lineData = this.sample.getLineDataset();
  readonly lineMultiWorkflow = this.sample.getLineMultiWorkflow();
  readonly lineMultiData = this.sample.getLineMultiDataset();
  readonly areaWorkflow = this.sample.getAreaWorkflow();
  readonly areaData = this.sample.getAreaDataset();
  readonly areaStackedWorkflow = this.sample.getAreaStackedWorkflow();
  readonly areaStackedData = this.sample.getAreaStackedDataset();
  readonly scatterWorkflow = this.sample.getScatterWorkflow();
  readonly scatterData = this.sample.getScatterDataset();
  readonly bubbleWorkflow = this.sample.getBubbleWorkflow();
  readonly bubbleData = this.sample.getBubbleDataset();
  readonly heatmapWorkflow = this.sample.getHeatmapWorkflow();
  readonly heatmapData = this.sample.getHeatmapDataset();
  readonly choroplethBfWorkflow = this.sample.getChoroplethBfWorkflow();
  readonly choroplethBfData = this.sample.getChoroplethBfDataset();
  readonly gaugeSemiWorkflow = this.sample.getGaugeSemiWorkflow();
  readonly gaugeSemiData = this.sample.getGaugeSemiDataset();
  readonly kpiTileWorkflow = this.sample.getKpiTileWorkflow();
  readonly kpiTileData = this.sample.getKpiTileDataset();
  readonly sparklineWorkflow = this.sample.getSparklineWorkflow();
  readonly sparklineData = this.sample.getSparklineDataset();
  readonly pivotTableWorkflow = this.sample.getPivotTableWorkflow();
  readonly pivotTableData = this.sample.getPivotTableDataset();
}
