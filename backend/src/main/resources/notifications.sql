-- Read only: derive updates from durable business records, never from fake events.
WITH events AS (
 SELECT 'order:'||o.id AS event_key,'Order received' AS title,o.order_number AS reference,NULL::text AS state,'#order/'||o.order_number AS target,o.created_at AS occurred_at
 FROM orders o WHERE o.user_id=:user
 UNION ALL
 SELECT 'delivery:'||h.id,'Delivery status updated',o.order_number,h.to_status,'#order/'||o.order_number,h.created_at
 FROM merchant_order_history h JOIN orders o ON o.id=h.order_id WHERE o.user_id=:user
 UNION ALL
 SELECT 'message:'||m.id,'New store message',c.name,NULL,CASE WHEN m.customer_id=:user THEN '#account' ELSE '#merchant' END,m.created_at
 FROM store_messages m JOIN companies c ON c.id=m.company_id
 WHERE m.sender_id<>:user AND (m.customer_id=:user OR EXISTS(SELECT 1 FROM merchant_stores s WHERE s.company_id=m.company_id AND s.user_id=:user))
 UNION ALL
 SELECT 'sale:'||m.order_id||':'||m.company_id,'New store order',o.order_number,NULL,'#merchant',o.created_at
 FROM merchant_orders m JOIN orders o ON o.id=m.order_id JOIN merchant_stores s ON s.company_id=m.company_id WHERE s.user_id=:user
 UNION ALL
 SELECT 'moderation:'||h.id,'Store review updated',c.name,h.status,'#merchant',h.created_at
 FROM store_moderation_history h JOIN companies c ON c.id=h.company_id JOIN merchant_stores s ON s.company_id=c.id WHERE s.user_id=:user
 UNION ALL
 SELECT 'application:'||s.company_id,'New business application',c.name,NULL,'#marketplace-admin',s.created_at
 FROM merchant_stores s JOIN companies c ON c.id=s.company_id WHERE :manager
 UNION ALL
 SELECT 'workflow:'||o.id,'Order needs attention',o.order_number,NULL,CASE WHEN :manager THEN '#admin-orders' ELSE '#staff-orders' END,o.created_at
 FROM orders o WHERE :manager OR :employee
 UNION ALL
 SELECT 'support:'||t.id,'New support request','SUP-'||t.id,NULL,'#support/'||t.id,t.created_at
 FROM support_tickets t WHERE :manager AND t.requester_id<>:user
 UNION ALL
 SELECT 'reply:'||m.id,'New support message','SUP-'||t.id,NULL,'#support/'||t.id,m.created_at
 FROM support_messages m JOIN support_tickets t ON t.id=m.ticket_id
 WHERE m.sender_id<>:user AND (:manager OR t.requester_id=:user)
 AND EXISTS(SELECT 1 FROM support_messages first_message WHERE first_message.ticket_id=t.id AND first_message.id<m.id)
 UNION ALL
 SELECT 'support-state:'||t.id||':'||t.version,'Support status updated','SUP-'||t.id,t.status,'#support/'||t.id,t.status_changed_at
 FROM support_tickets t WHERE t.requester_id=:user AND t.version>0
), recent AS (
 SELECT * FROM events WHERE occurred_at>CURRENT_TIMESTAMP-INTERVAL '90 days'
 AND (NOT :receipts OR event_key IN (:keys))
 ORDER BY occurred_at DESC,event_key DESC LIMIT 100
)
SELECT e.*,r.read_at IS NOT NULL AS is_read FROM recent e LEFT JOIN notification_reads r ON r.event_key=e.event_key AND r.user_id=:user
ORDER BY e.occurred_at DESC,e.event_key DESC
