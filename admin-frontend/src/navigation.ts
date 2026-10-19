import type { IconName } from './components/Icon';

export type View = 'overview' | 'pan' | 'lawyers';

export interface NavItem {
  id: View;
  label: string;
  icon: IconName;
}

export const NAV_ITEMS: NavItem[] = [
  { id: 'overview', label: 'Overview', icon: 'overview' },
  { id: 'pan', label: 'PAN review', icon: 'pan' },
  { id: 'lawyers', label: 'Lawyer approvals', icon: 'lawyers' },
];

export const VIEW_META: Record<View, { title: string; eyebrow: string }> = {
  overview: { title: 'Overview', eyebrow: 'Operations desk' },
  pan: { title: 'PAN review', eyebrow: 'Document queue' },
  lawyers: { title: 'Lawyer approvals', eyebrow: 'Credential queue' },
};
