import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { NgxEchartsDirective } from 'ngx-echarts';
import * as echarts from 'echarts/core';
import type { EChartsCoreOption } from 'echarts/core';
import bfGeoJson from './bf-geo.json';
import {
  AreaEncoding,
  AreaStackedEncoding,
  Bar100PctEncoding,
  BarGroupedEncoding,
  BarHorizontalEncoding,
  BarStackedEncoding,
  BarVerticalEncoding,
  BubbleEncoding,
  ChoroplethBfEncoding,
  ComboDualAxisEncoding,
  DonutEncoding,
  GaugeSemiEncoding,
  HalfDonutEncoding,
  HeatmapEncoding,
  KpiTileEncoding,
  LineEncoding,
  LineMultiEncoding,
  PieEncoding,
  Polarity,
  ScatterEncoding,
  SparklineEncoding,
  Thresholds,
  Visualization,
  WorkflowDefinition,
} from './workflow-definition.model';

export interface DemoDataset {
  rows: Array<Record<string, string | number>>;
}

export interface KpiTileModel {
  kind: 'kpi';
  value: string;
  delta?: string;
  trend?: 'up' | 'down' | 'flat';
  spark?: number[];
  title?: string;
  subtitle?: string;
}

const FASO_COLORS = {
  neutral: '#7B61FF',
  positive: '#1FAB5A',
  warning: '#F0A12A',
  critical: '#E0413B',
  violet: '#7B61FF',
};

const SEQUENTIAL_BLUE = ['#E3F2FD', '#90CAF9', '#42A5F5', '#1E88E5', '#1565C0', '#0D47A1'];
const SEQUENTIAL_GREEN = ['#E8F5E9', '#A5D6A7', '#66BB6A', '#43A047', '#2E7D32', '#1B5E20'];
const DIVERGING_RDYLGN = ['#D7191C', '#FDAE61', '#FFFFBF', '#A6D96A', '#1A9641'];
const CATEGORICAL_SET1 = [
  '#7B61FF', '#1FAB5A', '#F0A12A', '#E0413B', '#2196F3',
  '#9C27B0', '#FF9800', '#009688', '#673AB7', '#FF5722',
  '#3F51B5', '#795548', '#607D8B',
];

let BF_MAP_REGISTERED = false;
function ensureBfMapRegistered(): void {
  if (BF_MAP_REGISTERED) return;
  // The public types for registerMap are narrower than the supported runtime
  // input (object literal), so we cast through unknown.
  (echarts as unknown as { registerMap: (name: string, geo: unknown) => void }).registerMap(
    'BF',
    bfGeoJson,
  );
  BF_MAP_REGISTERED = true;
}

/**
 * EchartsRendererComponent — universal ECharts-based widget renderer.
 *
 * Supports 17 visualization types (BAR_GROUPED, HALF_DONUT, COMBO_DUAL_AXIS from
 * Phase 1 + the 14 viz types added in Phase 3). PIVOT_TABLE is delegated to
 * pivot-table-renderer.component.ts because ag-grid is more appropriate than
 * ECharts for pivot tables.
 *
 * ## Color & polarity rules (D5 du PLAN-002)
 *
 *  - `polarity: 'more_better'` (default) — higher value is better:
 *      * value >= warningPct → positive (var(--polarity-positive), green)
 *      * value >= criticalPct → warning  (var(--polarity-warning), amber)
 *      * value <  criticalPct → critical (var(--polarity-critical), red)
 *  - `polarity: 'less_better'` — invert the buckets (e.g. mortality rate). The
 *    inversion happens both on the comparison operator AND the threshold order:
 *    low values are good, high values are bad.
 *  - `polarity: 'neutral'` — purely informational; uses
 *    `var(--polarity-neutral)` (FASO violet) for the main series.
 *  - Loading state (Phase 4 wiring) — host element pulses violet for 3 s.
 *  - Cache hit  → bolt icon top-right (Phase 4 will toggle).
 *  - Stale freshness (>24h) → amber "Stale" badge (Phase 4 will toggle).
 *
 * Phase 3 ships static defaults: every widget uses `more_better` with
 * thresholds 75/60 % unless overridden in the WorkflowDefinition.
 */
@Component({
  selector: 'app-echarts-renderer',
  standalone: true,
  imports: [CommonModule, NgxEchartsDirective],
  templateUrl: './echarts-renderer.component.html',
  styles: [
    `
      :host { display: block; width: 100%; }
      .chart-wrapper {
        position: relative;
        background: #fff;
        border: 1px solid #e3e3e8;
        border-radius: 8px;
        padding: 16px 16px 8px 16px;
        box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
      }
      .chart-host { height: 360px; width: 100%; }
      .chart-host.compact { height: 120px; }
      .chart-placeholder {
        padding: 24px;
        text-align: center;
        color: #666;
        font-style: italic;
      }
      .kpi-tile {
        display: flex;
        flex-direction: column;
        gap: 4px;
        padding: 4px 0 8px 0;
      }
      .kpi-tile .kpi-title { font-size: 0.9rem; color: #555; }
      .kpi-tile .kpi-subtitle { font-size: 0.75rem; color: #999; margin-top: -2px; }
      .kpi-tile .kpi-value {
        font-size: 2.4rem;
        font-weight: 700;
        color: var(--polarity-neutral, #7b61ff);
        line-height: 1.05;
      }
      .kpi-tile .kpi-delta { font-size: 0.85rem; font-weight: 600; }
      .kpi-tile .kpi-delta.up { color: var(--polarity-positive, #1fab5a); }
      .kpi-tile .kpi-delta.down { color: var(--polarity-critical, #e0413b); }
      .kpi-tile .kpi-delta.flat { color: #666; }
      .kpi-tile .kpi-spark { height: 50px; margin-top: 4px; }
    `,
  ],
})
export class EchartsRendererComponent implements OnChanges {
  @Input({ required: true }) workflowDef!: WorkflowDefinition;
  @Input() dataset: DemoDataset = { rows: [] };

  chartOptions: EChartsCoreOption | null = null;
  placeholderMessage: string | null = null;
  kpiTile: KpiTileModel | null = null;
  sparkOptions: EChartsCoreOption | null = null;
  compactHost = false;

  ngOnChanges(_changes: SimpleChanges): void {
    this.recompute();
  }

  private recompute(): void {
    const viz = this.workflowDef?.spec?.visualizations?.[0];
    this.kpiTile = null;
    this.sparkOptions = null;
    this.compactHost = false;
    if (!viz) {
      this.placeholderMessage = 'No visualization defined.';
      this.chartOptions = null;
      return;
    }
    this.placeholderMessage = null;
    switch (viz.type) {
      case 'BAR_VERTICAL': this.chartOptions = this.buildBarVertical(viz); return;
      case 'BAR_HORIZONTAL': this.chartOptions = this.buildBarHorizontal(viz); return;
      case 'BAR_GROUPED': this.chartOptions = this.buildBarGrouped(viz); return;
      case 'BAR_STACKED': this.chartOptions = this.buildBarStacked(viz); return;
      case 'BAR_100PCT': this.chartOptions = this.buildBar100Pct(viz); return;
      case 'PIE': this.chartOptions = this.buildPie(viz); return;
      case 'DONUT': this.chartOptions = this.buildDonut(viz); return;
      case 'HALF_DONUT': this.chartOptions = this.buildHalfDonut(viz); return;
      case 'LINE': this.chartOptions = this.buildLine(viz); return;
      case 'LINE_MULTI': this.chartOptions = this.buildLineMulti(viz); return;
      case 'AREA': this.chartOptions = this.buildArea(viz); return;
      case 'AREA_STACKED': this.chartOptions = this.buildAreaStacked(viz); return;
      case 'COMBO_DUAL_AXIS': this.chartOptions = this.buildComboDualAxis(viz); return;
      case 'SCATTER': this.chartOptions = this.buildScatter(viz); return;
      case 'BUBBLE': this.chartOptions = this.buildBubble(viz); return;
      case 'HEATMAP': this.chartOptions = this.buildHeatmap(viz); return;
      case 'CHOROPLETH_BF':
      case 'CHOROPLETH': this.chartOptions = this.buildChoroplethBf(viz); return;
      case 'GAUGE_SEMI': this.chartOptions = this.buildGaugeSemi(viz); return;
      case 'KPI_TILE':
        this.chartOptions = null;
        this.kpiTile = this.buildKpiTile(viz);
        return;
      case 'SPARKLINE':
        this.compactHost = true;
        this.chartOptions = this.buildSparkline(viz);
        return;
      default:
        this.placeholderMessage = `Visualization type "${viz.type}" not implemented yet.`;
        this.chartOptions = null;
        return;
    }
  }

  private polarityColor(value: number, polarity: Polarity, thresholds: Thresholds): string {
    const warn = thresholds.warningPct ?? 75;
    const crit = thresholds.criticalPct ?? 60;
    if (polarity === 'neutral') return FASO_COLORS.neutral;
    if (polarity === 'less_better') {
      // less_better: low values are good (inverted scale).
      if (value <= crit) return FASO_COLORS.positive;
      if (value <= warn) return FASO_COLORS.warning;
      return FASO_COLORS.critical;
    }
    if (value >= warn) return FASO_COLORS.positive;
    if (value >= crit) return FASO_COLORS.warning;
    return FASO_COLORS.critical;
  }

  private resolvePalette(viz: Visualization): string[] {
    switch (viz.style?.palette ?? 'faso_default') {
      case 'sequential_blue': return SEQUENTIAL_BLUE;
      case 'sequential_green': return SEQUENTIAL_GREEN;
      case 'diverging_rdylgn': return DIVERGING_RDYLGN;
      case 'categorical_set1': return CATEGORICAL_SET1;
      default: return CATEGORICAL_SET1;
    }
  }

  private titleBlock(viz: Visualization): { title: { text: string; subtext: string } } {
    return { title: { text: viz.title ?? '', subtext: viz.subtitle ?? '' } };
  }

  private buildBarVertical(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as BarVerticalEncoding;
    const xValues = this.dataset.rows.map((r) => String(r[enc.xField]));
    const yValues = this.dataset.rows.map((r) => Number(r[enc.yField]));
    return {
      ...this.titleBlock(viz),
      color: this.resolvePalette(viz),
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 50, right: 24, top: viz.subtitle ? 70 : 50, bottom: 60 },
      xAxis: { type: 'category', data: xValues, axisLabel: { rotate: 35 } },
      yAxis: { type: 'value' },
      series: [{
        name: enc.yField,
        type: 'bar',
        data: yValues,
        itemStyle: { color: FASO_COLORS.neutral },
        emphasis: { focus: 'series' },
      }],
    };
  }

  private buildBarHorizontal(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as BarHorizontalEncoding;
    const yValues = this.dataset.rows.map((r) => String(r[enc.yField]));
    const xValues = this.dataset.rows.map((r) => Number(r[enc.xField]));
    return {
      ...this.titleBlock(viz),
      color: this.resolvePalette(viz),
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 140, right: 24, top: viz.subtitle ? 70 : 50, bottom: 40 },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: yValues, inverse: true },
      series: [{
        name: enc.xField,
        type: 'bar',
        data: xValues,
        itemStyle: { color: FASO_COLORS.neutral },
        emphasis: { focus: 'series' },
      }],
    };
  }

  private buildBarGrouped(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as BarGroupedEncoding;
    const xValues = Array.from(new Set(this.dataset.rows.map((r) => String(r[enc.xField]))));
    const seriesKeys = Array.from(new Set(this.dataset.rows.map((r) => String(r[enc.series]))));
    const series = seriesKeys.map((sk) => ({
      name: sk,
      type: 'bar' as const,
      barGap: 0,
      emphasis: { focus: 'series' as const },
      data: xValues.map((x) => {
        const found = this.dataset.rows.find(
          (r) => String(r[enc.xField]) === x && String(r[enc.series]) === sk,
        );
        return found ? Number(found[enc.yField]) : 0;
      }),
    }));
    return {
      ...this.titleBlock(viz),
      color: this.resolvePalette(viz),
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: seriesKeys, top: viz.subtitle ? 50 : 30 },
      grid: { left: 50, right: 24, top: viz.subtitle ? 90 : 70, bottom: 50 },
      xAxis: { type: 'category', data: xValues, axisLabel: { rotate: 35 } },
      yAxis: { type: 'value' },
      series,
    };
  }

  private buildBarStacked(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as BarStackedEncoding;
    const xValues = Array.from(new Set(this.dataset.rows.map((r) => String(r[enc.xField]))));
    const seriesKeys = Array.from(new Set(this.dataset.rows.map((r) => String(r[enc.series]))));
    const series = seriesKeys.map((sk) => ({
      name: sk,
      type: 'bar' as const,
      stack: 'a',
      emphasis: { focus: 'series' as const },
      data: xValues.map((x) => {
        const found = this.dataset.rows.find(
          (r) => String(r[enc.xField]) === x && String(r[enc.series]) === sk,
        );
        return found ? Number(found[enc.yField]) : 0;
      }),
    }));
    return {
      ...this.titleBlock(viz),
      color: this.resolvePalette(viz),
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: seriesKeys, top: viz.subtitle ? 50 : 30 },
      grid: { left: 50, right: 24, top: viz.subtitle ? 90 : 70, bottom: 50 },
      xAxis: { type: 'category', data: xValues, axisLabel: { rotate: 35 } },
      yAxis: { type: 'value' },
      series,
    };
  }

  private buildBar100Pct(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as Bar100PctEncoding;
    const xValues = Array.from(new Set(this.dataset.rows.map((r) => String(r[enc.xField]))));
    const seriesKeys = Array.from(new Set(this.dataset.rows.map((r) => String(r[enc.series]))));
    const totals = new Map<string, number>();
    xValues.forEach((x) => {
      const sum = this.dataset.rows
        .filter((r) => String(r[enc.xField]) === x)
        .reduce((acc, r) => acc + Number(r[enc.yField]), 0);
      totals.set(x, sum === 0 ? 1 : sum);
    });
    const series = seriesKeys.map((sk) => ({
      name: sk,
      type: 'bar' as const,
      stack: 'total',
      emphasis: { focus: 'series' as const },
      label: { show: true, formatter: '{c}%', position: 'inside' as const },
      data: xValues.map((x) => {
        const found = this.dataset.rows.find(
          (r) => String(r[enc.xField]) === x && String(r[enc.series]) === sk,
        );
        const raw = found ? Number(found[enc.yField]) : 0;
        return Math.round((raw / (totals.get(x) ?? 1)) * 1000) / 10;
      }),
    }));
    return {
      ...this.titleBlock(viz),
      color: this.resolvePalette(viz),
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: seriesKeys, top: viz.subtitle ? 50 : 30 },
      grid: { left: 50, right: 24, top: viz.subtitle ? 90 : 70, bottom: 50 },
      xAxis: { type: 'category', data: xValues, axisLabel: { rotate: 35 } },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series,
    };
  }

  private buildPie(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as PieEncoding;
    const data = this.dataset.rows.map((r) => ({
      name: String(r[enc.category]),
      value: Number(r[enc.value]),
    }));
    return {
      ...this.titleBlock(viz),
      color: this.resolvePalette(viz),
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll' },
      series: [{
        name: enc.category,
        type: 'pie',
        radius: '60%',
        avoidLabelOverlap: true,
        label: { show: true, formatter: '{b}\n{d}%' },
        data,
      }],
    };
  }

  private buildDonut(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as DonutEncoding;
    const data = this.dataset.rows.map((r) => ({
      name: String(r[enc.category]),
      value: Number(r[enc.value]),
    }));
    return {
      ...this.titleBlock(viz),
      color: this.resolvePalette(viz),
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll' },
      series: [{
        name: enc.category,
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}\n{d}%' },
        data,
      }],
    };
  }

  private buildHalfDonut(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as HalfDonutEncoding;
    const data = this.dataset.rows.map((r) => ({
      name: String(r[enc.category]),
      value: Number(r[enc.value]),
    }));
    return {
      title: { text: viz.title ?? '', subtext: viz.subtitle ?? '', left: 'center' },
      color: this.resolvePalette(viz),
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, type: 'scroll' },
      series: [{
        name: enc.category,
        type: 'pie',
        radius: ['40%', '70%'],
        startAngle: 180,
        endAngle: 360,
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}\n{d}%' },
        data,
      }],
    };
  }

  private buildLine(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as LineEncoding;
    const xValues = this.dataset.rows.map((r) => String(r[enc.xField]));
    const yValues = this.dataset.rows.map((r) => Number(r[enc.yField]));
    return {
      ...this.titleBlock(viz),
      color: [FASO_COLORS.neutral],
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 24, top: viz.subtitle ? 70 : 50, bottom: 50 },
      xAxis: { type: 'category', data: xValues, boundaryGap: false },
      yAxis: { type: 'value' },
      series: [{
        name: enc.yField,
        type: 'line',
        data: yValues,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 2 },
      }],
    };
  }

  private buildLineMulti(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as LineMultiEncoding;
    const xValues = Array.from(new Set(this.dataset.rows.map((r) => String(r[enc.xField]))));
    const seriesKeys = Array.from(new Set(this.dataset.rows.map((r) => String(r[enc.series]))));
    const series = seriesKeys.map((sk) => ({
      name: sk,
      type: 'line' as const,
      smooth: true,
      showSymbol: false,
      emphasis: { focus: 'series' as const },
      data: xValues.map((x) => {
        const found = this.dataset.rows.find(
          (r) => String(r[enc.xField]) === x && String(r[enc.series]) === sk,
        );
        return found ? Number(found[enc.yField]) : 0;
      }),
    }));
    return {
      ...this.titleBlock(viz),
      color: this.resolvePalette(viz),
      tooltip: { trigger: 'axis' },
      legend: { data: seriesKeys, top: viz.subtitle ? 50 : 30, type: 'scroll' },
      grid: { left: 50, right: 24, top: viz.subtitle ? 90 : 70, bottom: 50 },
      xAxis: { type: 'category', data: xValues, boundaryGap: false },
      yAxis: { type: 'value' },
      series,
    };
  }

  private buildArea(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as AreaEncoding;
    const xValues = this.dataset.rows.map((r) => String(r[enc.xField]));
    const yValues = this.dataset.rows.map((r) => Number(r[enc.yField]));
    return {
      ...this.titleBlock(viz),
      color: [FASO_COLORS.neutral],
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 24, top: viz.subtitle ? 70 : 50, bottom: 50 },
      xAxis: { type: 'category', data: xValues, boundaryGap: false },
      yAxis: { type: 'value' },
      series: [{
        name: enc.yField,
        type: 'line',
        data: yValues,
        smooth: true,
        showSymbol: false,
        areaStyle: {},
        lineStyle: { width: 2 },
      }],
    };
  }

  private buildAreaStacked(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as AreaStackedEncoding;
    const xValues = Array.from(new Set(this.dataset.rows.map((r) => String(r[enc.xField]))));
    const seriesKeys = Array.from(new Set(this.dataset.rows.map((r) => String(r[enc.series]))));
    const series = seriesKeys.map((sk) => ({
      name: sk,
      type: 'line' as const,
      stack: 'a',
      smooth: true,
      showSymbol: false,
      areaStyle: {},
      emphasis: { focus: 'series' as const },
      data: xValues.map((x) => {
        const found = this.dataset.rows.find(
          (r) => String(r[enc.xField]) === x && String(r[enc.series]) === sk,
        );
        return found ? Number(found[enc.yField]) : 0;
      }),
    }));
    return {
      ...this.titleBlock(viz),
      color: this.resolvePalette(viz),
      tooltip: { trigger: 'axis' },
      legend: { data: seriesKeys, top: viz.subtitle ? 50 : 30 },
      grid: { left: 50, right: 24, top: viz.subtitle ? 90 : 70, bottom: 50 },
      xAxis: { type: 'category', data: xValues, boundaryGap: false },
      yAxis: { type: 'value' },
      series,
    };
  }

  private buildComboDualAxis(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as ComboDualAxisEncoding;
    const xValues = this.dataset.rows.map((r) => String(r[enc.xField]));
    const leftData = this.dataset.rows.map((r) => Number(r[enc.leftAxis.field]));
    const rightData = this.dataset.rows.map((r) => Number(r[enc.rightAxis.field]));
    return {
      ...this.titleBlock(viz),
      color: this.resolvePalette(viz),
      tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
      legend: {
        data: [enc.leftAxis.label ?? enc.leftAxis.field, enc.rightAxis.label ?? enc.rightAxis.field],
        top: viz.subtitle ? 50 : 30,
      },
      grid: { left: 60, right: 60, top: viz.subtitle ? 90 : 70, bottom: 50 },
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

  private buildScatter(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as ScatterEncoding;
    const points = this.dataset.rows.map((r) => [
      Number(r[enc.xField]),
      Number(r[enc.yField]),
    ]);
    return {
      ...this.titleBlock(viz),
      color: [FASO_COLORS.neutral],
      tooltip: { trigger: 'item' },
      grid: { left: 50, right: 24, top: viz.subtitle ? 70 : 50, bottom: 50 },
      xAxis: { type: 'value', name: enc.xField, scale: true },
      yAxis: { type: 'value', name: enc.yField, scale: true },
      series: [{
        name: `${enc.xField} × ${enc.yField}`,
        type: 'scatter',
        symbolSize: 10,
        data: points,
      }],
    };
  }

  private buildBubble(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as BubbleEncoding;
    const points = this.dataset.rows.map((r) => [
      Number(r[enc.xField]),
      Number(r[enc.yField]),
      Number(r[enc.size]),
    ]);
    const sizes = points.map((p) => p[2] ?? 0);
    const sMin = sizes.length ? Math.min(...sizes) : 0;
    const sMax = sizes.length ? Math.max(...sizes) : 1;
    const range = sMax - sMin || 1;
    return {
      ...this.titleBlock(viz),
      color: [FASO_COLORS.neutral],
      tooltip: { trigger: 'item' },
      grid: { left: 60, right: 24, top: viz.subtitle ? 70 : 50, bottom: 60 },
      xAxis: { type: 'value', name: enc.xField, scale: true },
      yAxis: { type: 'value', name: enc.yField, scale: true },
      series: [{
        name: enc.size,
        type: 'scatter',
        data: points,
        symbolSize: (val: number[]) => 12 + ((val[2] - sMin) / range) * 48,
      }],
    };
  }

  private buildHeatmap(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as HeatmapEncoding;
    const xValues = Array.from(new Set(this.dataset.rows.map((r) => String(r[enc.xField]))));
    const yValues = Array.from(new Set(this.dataset.rows.map((r) => String(r[enc.yField]))));
    const data = this.dataset.rows.map((r) => [
      xValues.indexOf(String(r[enc.xField])),
      yValues.indexOf(String(r[enc.yField])),
      Number(r[enc.value]),
    ]);
    const values = data.map((d) => d[2] ?? 0);
    return {
      ...this.titleBlock(viz),
      tooltip: { position: 'top' },
      grid: { left: 80, right: 60, top: viz.subtitle ? 70 : 50, bottom: 60 },
      xAxis: { type: 'category', data: xValues, splitArea: { show: true } },
      yAxis: { type: 'category', data: yValues, splitArea: { show: true } },
      visualMap: {
        min: values.length ? Math.min(...values) : 0,
        max: values.length ? Math.max(...values) : 100,
        calculable: true,
        orient: 'horizontal',
        left: 'center',
        bottom: 0,
        inRange: { color: SEQUENTIAL_BLUE },
      },
      series: [{
        name: enc.value,
        type: 'heatmap',
        data,
        label: { show: true },
        emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.5)' } },
      }],
    };
  }

  private buildChoroplethBf(viz: Visualization): EChartsCoreOption {
    // Register synchronously here — the directive paints the canvas only after
    // build() returns, so the map name is always resolved before setOption.
    ensureBfMapRegistered();
    const enc = viz.encoding as unknown as ChoroplethBfEncoding;
    const data = this.dataset.rows.map((r) => ({
      name: String(r[enc.regionField]),
      value: Number(r[enc.valueField]),
    }));
    const values = data.map((d) => d.value).filter((v) => Number.isFinite(v));
    const min = values.length ? Math.min(...values) : 0;
    const max = values.length ? Math.max(...values) : 100;
    return {
      ...this.titleBlock(viz),
      tooltip: { trigger: 'item', formatter: '{b}: {c}' },
      visualMap: {
        min,
        max,
        left: 'left',
        bottom: 20,
        calculable: true,
        inRange: { color: DIVERGING_RDYLGN },
      },
      series: [{
        name: enc.valueField,
        type: 'map',
        map: 'BF',
        roam: true,
        emphasis: { label: { show: true } },
        data,
      }],
    };
  }

  private buildGaugeSemi(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as GaugeSemiEncoding;
    const firstRow = this.dataset.rows[0] ?? {};
    const value = Number(firstRow[enc.value] ?? 0);
    const targetValue = enc.target ? Number(firstRow[enc.target] ?? 100) : 100;
    const polarity: Polarity = viz.polarity ?? 'more_better';
    const thresholds = viz.thresholds ?? { warningPct: 75, criticalPct: 60 };
    const color = this.polarityColor((value / targetValue) * 100, polarity, thresholds);
    return {
      ...this.titleBlock(viz),
      tooltip: { formatter: '{b}: {c}' },
      series: [{
        name: enc.value,
        type: 'gauge',
        startAngle: 180,
        endAngle: 0,
        min: 0,
        max: targetValue,
        radius: '90%',
        center: ['50%', '70%'],
        progress: { show: true, width: 18 },
        axisLine: { lineStyle: { width: 18, color: [[1, '#eee']] } },
        axisTick: { show: false },
        splitLine: { length: 12, lineStyle: { width: 2, color: '#999' } },
        axisLabel: { distance: 22, color: '#666', fontSize: 11 },
        pointer: { show: false },
        detail: {
          valueAnimation: true,
          formatter: '{value}',
          color,
          fontSize: 28,
          offsetCenter: [0, '-10%'],
        },
        title: { offsetCenter: [0, '20%'], fontSize: 13, color: '#555' },
        data: [{ value, name: enc.target ? `cible ${targetValue}` : '', itemStyle: { color } }],
      }],
    };
  }

  private buildKpiTile(viz: Visualization): KpiTileModel {
    const enc = viz.encoding as unknown as KpiTileEncoding;
    const firstRow = this.dataset.rows[0] ?? {};
    const value = String(firstRow[enc.value] ?? '—');
    const deltaRaw = enc.delta ? Number(firstRow[enc.delta] ?? 0) : 0;
    const trend: 'up' | 'down' | 'flat' = deltaRaw > 0 ? 'up' : deltaRaw < 0 ? 'down' : 'flat';
    const arrow = trend === 'up' ? '↑' : trend === 'down' ? '↓' : '→';
    const delta = enc.delta
      ? `${arrow} ${deltaRaw > 0 ? '+' : ''}${deltaRaw.toFixed(1)} % vs N-1`
      : undefined;
    let spark: number[] | undefined;
    if (enc.trend) {
      const trendKey = enc.trend;
      spark = this.dataset.rows
        .map((r) => Number(r[trendKey]))
        .filter((n) => Number.isFinite(n));
    }
    if (spark && spark.length > 1) {
      this.sparkOptions = {
        grid: { left: 0, right: 0, top: 4, bottom: 4 },
        xAxis: { type: 'category', show: false, boundaryGap: false, data: spark.map((_, i) => i) },
        yAxis: { type: 'value', show: false },
        tooltip: { show: false },
        series: [{
          type: 'line',
          data: spark,
          smooth: true,
          showSymbol: false,
          lineStyle: { color: FASO_COLORS.neutral, width: 2 },
          areaStyle: { color: 'rgba(123,97,255,0.18)' },
        }],
      };
    }
    return { kind: 'kpi', value, delta, trend, spark, title: viz.title, subtitle: viz.subtitle };
  }

  private buildSparkline(viz: Visualization): EChartsCoreOption {
    const enc = viz.encoding as unknown as SparklineEncoding;
    const values = this.dataset.rows.map((r) => Number(r[enc.yField]));
    return {
      grid: { left: 0, right: 0, top: 4, bottom: 4 },
      xAxis: { type: 'category', show: false, boundaryGap: false, data: values.map((_, i) => i) },
      yAxis: { type: 'value', show: false },
      tooltip: { trigger: 'axis', show: true },
      series: [{
        type: 'line',
        data: values,
        smooth: true,
        showSymbol: false,
        lineStyle: { color: FASO_COLORS.neutral, width: 2 },
        areaStyle: { color: 'rgba(123,97,255,0.18)' },
      }],
    };
  }
}
