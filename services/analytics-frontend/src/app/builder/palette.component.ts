import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { DragDropModule } from '@angular/cdk/drag-drop';
import {
  NodeCategory,
  PaletteItem,
  PALETTE_ITEMS,
} from './dag-node.model';

interface PaletteSection {
  category: NodeCategory;
  items: PaletteItem[];
}

@Component({
  selector: 'fd-palette',
  standalone: true,
  imports: [CommonModule, DragDropModule],
  templateUrl: './palette.component.html',
})
export class PaletteComponent {
  readonly sections: PaletteSection[] = (
    ['Source', 'Transform', 'KPI/Viz', 'Output'] as NodeCategory[]
  ).map((category) => ({
    category,
    items: PALETTE_ITEMS.filter((i) => i.category === category),
  }));
}
