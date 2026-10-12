export interface Membership {
  workspace_id: string;
  workspace_name: string;
  organization_id: string;
  organization_name: string;
  role: 'OWNER' | 'OPERATOR' | 'PARTNER_OPERATOR' | 'AUDITOR';
  recall_manager: boolean;
  network_read: boolean;
  active: boolean;
}
export interface Session {
  accessToken: string;
  user: { id: string; email: string; name: string };
  memberships: Membership[];
}
export interface Organization {
  id: string;
  name: string;
}
export interface Site {
  id: string;
  name: string;
  organization_id: string;
  organization_name: string;
}
export interface Product {
  id: string;
  name: string;
  base_unit: string;
}
export interface Lot {
  id: string;
  code: string;
  product_id: string;
  product_name: string;
  base_unit: string;
  issuer_id: string;
  issuer_name: string;
  issued_on: string;
  expires_on: string | null;
  created_sequence: number;
}
export interface Holding {
  lot_id: string;
  code: string;
  product_name: string;
  base_unit: string;
  site_id: string;
  site_name: string;
  organization_id: string;
  organization_name: string;
  status: string;
  transfer_id: string | null;
  quantity: string;
}
export interface Lineage {
  input_lot_id: string;
  output_lot_id: string;
  operation_id: string;
  sequence: number;
}
export interface EventFact {
  id: string;
  sequence: number;
  type: string;
  actor_id: string;
  actor_name: string;
  organization_id: string;
  organization_name: string;
  payload: Record<string, unknown>;
  recorded_at: string;
  occurred_at: string;
  command_id: string;
  schema_version: number;
}
export interface Graph {
  sequence: number;
  historical: boolean;
  complete: boolean;
  scope: string;
  lots: Lot[];
  lineage: Lineage[];
  holdings: Holding[];
  events: EventFact[];
  transfers: Transfer[];
}
export interface Transfer {
  id: string;
  reference: string;
  source_site_id: string;
  destination_site_id: string;
  source_site?: string;
  destination_site?: string;
  source_organization_id: string;
  destination_organization_id: string;
  source_organization?: string;
  destination_organization?: string;
  status: string;
  version: number;
}
export interface TransferLine {
  lot_id: string;
  code: string;
  quantity: string;
  base_unit: string;
  reserved: boolean;
  held: boolean;
}
export interface Receipt {
  id: string;
  lot_id: string;
  received_quantity: string;
  condition: string;
  reason: string;
  actor_name: string;
  occurred_at: string;
  recorded_at: string;
}
export interface Discrepancy {
  id: string;
  lot_id: string;
  sent_quantity: string;
  received_quantity: string;
  unresolved_quantity: string;
}
export interface TransferDetail {
  transfer: Transfer;
  lines: TransferLine[];
  receipts: Receipt[];
  discrepancies: Discrepancy[];
  resolutions: Record<string, unknown>[];
}
export interface Recall {
  id: string;
  title: string;
  reason: string;
  status: string;
  version: number;
  activation_sequence: number;
  base_unit?: string;
  roots?: string[];
}
export interface Target {
  id: string;
  lot_id: string;
  code: string;
  base_unit: string;
  site_id: string;
  site_name: string;
  organization_id: string;
  organization_name: string;
  transfer_id: string | null;
  quantity: string;
  accounted: string;
  unresolved: string;
  acknowledged: boolean;
}
export interface RecallDetail {
  recall: Recall;
  targets: Target[];
  revisions: { revision: number; sequence: number; reason: string; scope?: unknown }[];
  acknowledgments: {
    actor_name: string;
    organization_id: string;
    note: string;
    sequence: number;
  }[];
  closure: {
    canClose: boolean;
    unansweredOrganizations: number;
    unaccountedQuantity: string;
    openEvidenceGaps: number;
  };
  policy: string;
}
export interface Preview {
  baseUnit: string;
  sequence: number;
  complete: boolean;
  coverage: string;
  roots: string[];
  affectedLots: { lotId: string; path: Lineage[] }[];
  exposure: Holding[];
  unaffected: Holding[];
  totals: Record<string, string>;
  gaps: { kind: string; quantity: string; message: string }[];
}
export interface Workspace {
  workspace: { id: string; name: string; sequence: number };
  organizations: Organization[];
  sites: Site[];
  members: {
    id: string;
    name: string;
    email: string;
    organization_id: string;
    role: string;
    recall_manager: boolean;
    network_read: boolean;
    site_ids: string[];
  }[];
  invitations: {
    id: string;
    email: string;
    role: string;
    expires_at: string;
    used_at: string | null;
  }[];
}
export interface Notification {
  id: string;
  event_id: string;
  message: string;
  created_at: string;
  read_at: string | null;
}
export interface Accepted {
  id: string;
  sequence: number;
  commandId: string;
  outputs?: { id: string; code: string; quantity: string }[];
  invitationPath?: string;
}
