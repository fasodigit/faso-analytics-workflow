import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import type { EChartsCoreOption } from 'echarts/core';
import {
  BarGroupedEncoding,
  ComboDualAxisEncoding,
  HalfDonutEncoding,
  WorkflowDefinition,
} from './workflow-definition.model';

export interface DemoDataset {
  rows: Array<Record<string, string | number>>;
}

@Component({
  selector: 'app-echarts-renderer',
  standalone: true,
  imports: [CommonModule, NgxEchartsDirective],
  templateUrl: './echarts-renderer.component.html',
  styles: [
    `
      :host {
        display: block;
        width: 100%;
      }
      .chart-wrapper {
        background: #fff;
        border: 1px solid #e3e3e8;
        border-radius: 8px;
        padding: 16px 16px 8px 16px;
        box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
      }
      .chart-host {
        height: 360px;
        width: 100%;
      }
      .chart-placeholder {
        padding: 24px;
        text-align: center;
        color: #666;
        font-style: italic;
      }
    `,
  ],
})
export class EchartsRendererComponent implements OnChanges {
  @Input({ required: true }) workflowDef!: WorkflowDefinition;
  @Input() dataset: DemoDataset = { rows: [] };

  chartOptions: EChartsCoreOption | null = null;
  placeholderMessage: string | null = null;

  ngOnChanges(_changes: SimpleChanges): void {
    this.recompute();
  }

  private recompute(): void {
    const viz = this.workflowDef?.spec?.visualizations?.[0];
    if (!viz) {
      this.placeholderMessage = 'No visualization defined.';
      this.chartOptions = null;
      return;
    }
    switch (viz.type) {
      case 'BAR_GROUPED':
        this.placeholderMessage = null;
        this.chartOptions = this.buildBarGrouped(
          viz.encoding as BarGroupedEncoding,
          viz.title,
          viz.subtitle,
        );
        return;
      case 'HALF_DONUT':
        this.placeholderMessage = null;
        this.chartOptions = this.buildHalfDonut(
          viz.encoding as HalfDonutEncoding,
          viz.title,
          viz.subtitle,
        );
        return;
      case 'COMBO_DUAL_AXIS':
        this.placeholderMessage = null;
        this.chartOptions = this.buildComboDualAxis(
          viz.encoding as ComboDualAxisEncoding,
          viz.title,
          viz.subtitle,
        );
        return;
      default:
        this.placeholderMessage = `Visualization type "${viz.type}" not implemented yet in Phase 1.`;
        this.chartOptions = null;
        return;
    }
  }

  private buildBarGrouped(
    enc: BarGroupedEncoding,
    title?: string,
    subtitle?: string,
  ): EChartsCoreOption {
    const xValues = Array.from(
      new Set(this.dataset.rows.map((r) => String(r[enc.xField]))),
    );
    const seriesKeys = Array.from(
      new Set(this.dataset.rows.map((r) => String(r[enc.series]))),
    );
    const series = seriesKeys.map((sk) => ({
      name: sk,
      type: 'bar',
      barGap: 0,
      emphasis: { focus: 'series' },
      data: xValues.map((x) => {
        const found = this.dataset.rows.find(
          (r) => String(r[enc.xField]) === x && String(r[enc.series]) === sk,
        );
        return found ? Number(found[enc.yField]) : 0;
      }),
    }));
    return {
      title: { text: title ?? '', subtext: subtitle ?? '' },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: seriesKeys, top: subtitle ? 50 : 30 },
      grid: { left: 50, right: 24, top: subtitle ? 90 : 70, bottom: 50 },
      xAxis: { type: 'category', data: xValues, axisLabel: { rotate: 35 } },
      yAxis: { type: 'value' },
      series,
    };
  }

  private buildHalfDonut(
    enc: HalfDonutEncoding,
    title?: string,
    subtitle?: string,
  ): EChartsCoreOption {
    const data = this.dataset.rows.map((r) => ({
      name: String(r[enc.category]),
      value: Number(r[enc.value]),
    }));
    return {
      title: {
        text: title ?? '',
        subtext: subtitle ?? '',
        left: 'center',
      },
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll' },
      series: [
        {
          name: enc.category,
          type: 'pie',
          radius: ['40%', '70%'],
          startAngle: 180,
          endAngle: 360,
          avoidLabelOverlap: true,
          itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
          label: { show: true, formatter: '{b}\n{d}%' },
          data,
        },
      ],
    };
  }

  private buildComboDualAxis(
    enc: ComboDualAxisEncoding,
    title?: string,
    subtitle?: string,
  ): EChartsCoreOption {
    const xValues = this.dataset.rows.map((r) => String(r[enc.xField]));
    const leftData = this.dataset.rows.map((r) => Number(r[enc.leftAxis.field]));
    const rightData = this.dataset.rows.map((r) => Number(r[enc.rightAxis.field]));
    return {
      title: { text: title ?? '', subtext: subtitle ?? '' },
      tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
      legend: {
        data: [enc.leftAxis.label ?? enc.leftAxis.field, enc.rightAxis.label ?? enc.rightAxis.field],
        top: subtitle ? 50 : 30,
      },
      grid: { left: 60, right: 60, top: subtitle ? 90 : 70, bottom: 50 },
      xAxis: { type: 'category', data: xValues },
      yAxis: [
        {
          type: 'value',
          name: enc.leftAxis.label ?? enc.leftAxis.field,
          position: 'left',
          min: enc.leftAxis.min ?? undefined,
          axisLabel: { formatter: enc.leftAxis.format?.unit ? `{value} ${enc.leftAxis.format.unit}` : '{value}' },
        },
        {
          type: 'value',
          name: enc.rightAxis.label ?? enc.rightAxis.field,
          position: 'right',
          min: enc.rightAxis.min ?? undefined,
          axisLabel: { formatter: enc.rightAxis.format?.unit ? `{value} ${enc.rightAxis.format.unit}` : '{value}' },
        },
      ],
      series: [
        {
          name: enc.leftAxis.label ?? enc.leftAxis.field,
          type: enc.leftAxis.seriesType === 'BAR' ? 'bar' : 'line',
          yAxisIndex: 0,
          data: leftData,
        },
        {
          name: enc.rightAxis.label ?? enc.rightAxis.field,
          type: enc.rightAxis.seriesType === 'BAR' ? 'bar' : 'line',
          yAxisIndex: 1,
          data: rightData,
          smooth: true,
        },
      ],
    };
  }
}
