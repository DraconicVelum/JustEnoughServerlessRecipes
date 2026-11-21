package com.draconicvelum.justenoughserverlessrecipes.transfer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import java.util.ArrayList;
import java.util.List;

/** Pure simulation of vanilla trade selection followed by exact payment placement. */
final class MerchantTransferPlan {
    record Click(int slot, int button) {}
    record Plan(List<ItemStack> selectedSlots, List<Click> clicks, int trades) {}

    static Plan create(List<ItemStack> original, MerchantOffer offer, boolean maximum) {
        if (original.size() != 39 || offer.isOutOfStock()) return null;
        List<ItemStack> selected = copy(original);
        // MerchantMenu.tryMoveItems: return payments in reverse inventory order, then fill A and B.
        if (!returnPayment(selected, 0) || !returnPayment(selected, 1)) return null;
        fillAutomatically(selected, 0, offer.getItemCostA());
        offer.getItemCostB().ifPresent(cost -> fillAutomatically(selected, 1, cost));

        List<ItemStack> pool = copy(selected);
        // Allocation sees both existing payments and the inventory, but never the result slot.
        int limit = maximum ? Math.min(64, offer.getMaxUses() - offer.getUses()) : 1;
        for (int sets = limit; sets > 0; sets--) {
            for (ItemStack a : variants(pool, offer.getItemCostA())) {
                List<ItemStack> bVariants = offer.getItemCostB().map(cost -> variants(pool, cost))
                        .orElseGet(() -> List.of(ItemStack.EMPTY));
                for (ItemStack b : bVariants) {
                    int countA = offer.getCostA().getCount() * sets;
                    int countB = offer.getCostB().getCount() * sets;
                    if (countA > a.getMaxStackSize() || (!b.isEmpty() && countB > b.getMaxStackSize())) continue;
                    int neededA = countA;
                    if (!b.isEmpty() && ItemStack.isSameItemSameComponents(a, b)) neededA += countB;
                    if (available(pool, a) < neededA || (!b.isEmpty() && available(pool, b) < countB)) continue;
                    if (same(selected.get(0), a, countA) && same(selected.get(1), b, countB)) {
                        return new Plan(selected, List.of(), sets);
                    }
                    List<ItemStack> work = copy(selected);
                    List<Click> clicks = new ArrayList<>();
                    if (!clearPayment(work, clicks, 0) || !clearPayment(work, clicks, 1)) continue;
                    if (!place(work, clicks, 0, a, countA) || !place(work, clicks, 1, b, countB)) continue;
                    return new Plan(selected, List.copyOf(clicks), sets);
                }
            }
        }
        return null;
    }

    private static boolean same(ItemStack actual, ItemStack desired, int count) {
        return count == 0 ? actual.isEmpty() : actual.getCount() == count && ItemStack.isSameItemSameComponents(actual, desired);
    }

    private static List<ItemStack> copy(List<ItemStack> slots) {
        return new ArrayList<>(slots.stream().map(ItemStack::copy).toList());
    }

    private static List<ItemStack> variants(List<ItemStack> slots, ItemCost cost) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (i != 2 && !stack.isEmpty() && cost.test(stack)
                    && result.stream().noneMatch(other -> ItemStack.isSameItemSameComponents(stack, other))) result.add(stack);
        }
        return result;
    }

    private static int available(List<ItemStack> slots, ItemStack variant) {
        int count = 0;
        for (int i = 0; i < slots.size(); i++) {
            if (i != 2 && ItemStack.isSameItemSameComponents(slots.get(i), variant)) count += slots.get(i).getCount();
        }
        return count;
    }

    private static boolean returnPayment(List<ItemStack> slots, int payment) {
        return returnToInventory(slots, payment, null);
    }

    private static boolean clearPayment(List<ItemStack> slots, List<Click> clicks, int payment) {
        return returnToInventory(slots, payment, clicks);
    }

    private static boolean returnToInventory(List<ItemStack> slots, int payment, List<Click> clicks) {
        ItemStack carried = slots.get(payment).copy();
        if (carried.isEmpty()) return true;
        if (clicks != null) clicks.add(new Click(payment, 0));
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 38; i >= 3 && !carried.isEmpty(); i--) {
                ItemStack target = slots.get(i);
                if ((pass == 0 && !target.isEmpty() && ItemStack.isSameItemSameComponents(carried, target))
                        || (pass == 1 && target.isEmpty())) {
                    int moved = Math.min(carried.getCount(), carried.getMaxStackSize() - target.getCount());
                    if (moved > 0) {
                        slots.set(i, carried.copyWithCount(target.getCount() + moved));
                        carried.shrink(moved);
                        if (clicks != null) clicks.add(new Click(i, 0));
                    }
                }
            }
        }
        slots.set(payment, carried);
        return carried.isEmpty();
    }

    private static void fillAutomatically(List<ItemStack> slots, int payment, ItemCost cost) {
        for (int i = 3; i < 39; i++) {
            ItemStack source = slots.get(i);
            ItemStack target = slots.get(payment);
            if (source.isEmpty() || !cost.test(source)
                    || (!target.isEmpty() && !ItemStack.isSameItemSameComponents(source, target))) continue;
            int moved = Math.min(source.getCount(), source.getMaxStackSize() - target.getCount());
            slots.set(payment, source.copyWithCount(target.getCount() + moved));
            source.shrink(moved);
            if (slots.get(payment).getCount() == slots.get(payment).getMaxStackSize()) break;
        }
    }

    private static boolean place(List<ItemStack> slots, List<Click> clicks, int payment, ItemStack variant, int needed) {
        for (int i = 3; i < 39 && needed > 0; i++) {
            ItemStack source = slots.get(i);
            if (source.isEmpty() || !ItemStack.isSameItemSameComponents(source, variant)) continue;
            int moved = Math.min(source.getCount(), needed);
            int excess = source.getCount() - moved;
            clicks.add(new Click(i, 0));
            if (excess == 0) {
                clicks.add(new Click(payment, 0));
            } else if (excess < moved) {
                for (int n = 0; n < excess; n++) clicks.add(new Click(i, 1));
                clicks.add(new Click(payment, 0));
            } else {
                for (int n = 0; n < moved; n++) clicks.add(new Click(payment, 1));
                clicks.add(new Click(i, 0));
            }
            slots.set(payment, variant.copyWithCount(slots.get(payment).getCount() + moved));
            source.shrink(moved);
            needed -= moved;
        }
        return needed == 0;
    }
}
