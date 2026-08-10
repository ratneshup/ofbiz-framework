/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ofbiz.product.product.inventory

import java.math.BigDecimal
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityConditionList
import org.apache.ofbiz.entity.condition.EntityOperator
import org.apache.ofbiz.entity.model.DynamicViewEntity
import org.apache.ofbiz.entity.model.ModelKeyMap
import org.apache.ofbiz.service.ServiceUtil

/**
 * Returns available to order quantity for a product.
 */
Map getProductAvailableToOrder() {
    String productId = parameters.productId
    String payToPartyId = parameters.payToPartyId
    String excludeOrderId = parameters.orderId

    BigDecimal qoh = BigDecimal.ZERO
    BigDecimal ordered = BigDecimal.ZERO
    BigDecimal issued = BigDecimal.ZERO

    from('InventoryItem')
            .where(productId: productId, ownerPartyId: payToPartyId)
            .queryList()
            .each { item ->
                if (!item.statusId) {
                    BigDecimal qty = item.quantityOnHandTotal ?: BigDecimal.ZERO
                    qoh = qoh.add(qty)
                }
            }

    DynamicViewEntity orderView = new DynamicViewEntity()
    orderView.addMemberEntity('OI', 'OrderItem')
    orderView.addMemberEntity('OH', 'OrderHeader')
    orderView.addMemberEntity('PS', 'ProductStore')
    orderView.addViewLink('OI', 'OH', false, ModelKeyMap.makeKeyMapList('orderId'))
    orderView.addViewLink('OH', 'PS', false, ModelKeyMap.makeKeyMapList('productStoreId'))
    orderView.addAlias('OI', 'orderId', 'orderId', null, null, null, null)
    orderView.addAlias('OI', 'orderItemSeqId', 'orderItemSeqId', null, null, null, null)
    orderView.addAlias('OI', 'quantity', 'quantity', null, null, null, null)
    orderView.addAlias('OI', 'cancelQuantity', 'cancelQuantity', null, null, null, null)
    orderView.addAlias('OI', 'productId', 'productId', null, null, null, null)
    orderView.addAlias('OI', 'oiStatusId', 'statusId', null, null, null, null)
    orderView.addAlias('OH', 'ohStatusId', 'statusId', null, null, null, null)
    orderView.addAlias('OH', 'externalId', 'externalId', null, null, null, null)
    orderView.addAlias('PS', 'payToPartyId', 'payToPartyId', null, null, null, null)

    List conditions = [
            EntityCondition.makeCondition('productId', productId),
            EntityCondition.makeCondition('payToPartyId', payToPartyId),
            EntityCondition.makeCondition('oiStatusId', EntityOperator.IN, ['ITEM_APPROVED', 'ITEM_CREATED']),
            EntityCondition.makeCondition('ohStatusId', EntityOperator.IN,
                    ['ORDER_APPROVED', 'ORDER_CREATED', 'ORDER_PROCESSING', 'ORDER_HOLD']),
            EntityCondition.makeCondition('externalId', EntityOperator.EQUALS, null)
    ]
    if (excludeOrderId) {
        conditions.add(EntityCondition.makeCondition('orderId', EntityOperator.NOT_EQUAL, excludeOrderId))
    }

    EntityCondition condition = EntityCondition.makeCondition(conditions, EntityOperator.AND)
    from(orderView).where(condition).queryList().each { row ->
        BigDecimal qty = row.quantity ?: BigDecimal.ZERO
        BigDecimal cancelQty = row.cancelQuantity ?: BigDecimal.ZERO
        ordered = ordered.add(qty.subtract(cancelQty))
        from('ItemIssuance').where(orderId: row.orderId, orderItemSeqId: row.orderItemSeqId)
                .queryList().each { iss ->
                    issued = issued.add(iss.quantity ?: BigDecimal.ZERO)
                }
    }

    BigDecimal availableToOrder = qoh.subtract(ordered.subtract(issued))

    Map result = ServiceUtil.returnSuccess()
    result.availableToOrder = availableToOrder
    result.productId = productId
    return result
}
