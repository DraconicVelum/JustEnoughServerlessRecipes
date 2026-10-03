package com.draconicvelum.justenoughserverlessrecipes.transfer;

import net.minecraft.world.entity.player.Player;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IStackHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.transfer.RecipeTransferOperationsResult;
import mezz.jei.common.transfer.RecipeTransferUtil;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VanillaMenuTransferExecutor {
    private VanillaMenuTransferExecutor() {
    }

    public static boolean execute(
            AbstractContainerMenu container,
            Player player,
            IStackHelper stackHelper,
            IRecipeSlotsView recipeSlotsView,
            List<Slot> craftingSlots,
            List<Slot> inventorySlots,
            boolean maxTransfer,
            boolean requireCompleteSets
    ) {
        if (!container.getCarried().isEmpty()) {
            return false;
        }

        for (Slot craftingSlot : craftingSlots) {
            if (!craftingSlot.getItem().isEmpty()) {
                click(container.containerId, craftingSlot.index, 0, ContainerInput.QUICK_MOVE, player);
                if (!craftingSlot.getItem().isEmpty()) {
                    return false;
                }
            }
        }

        List<IRecipeSlotView> inputSlots = recipeSlotsView.getSlotViews(RecipeIngredientRole.INPUT);
        Map<Slot, ItemStack> availableItemStacks = new HashMap<>();
        for (Slot inventorySlot : inventorySlots) {
            ItemStack stack = inventorySlot.getItem();
            if (!stack.isEmpty()) {
                availableItemStacks.put(inventorySlot, stack.copy());
            }
        }

        RecipeTransferOperationsResult oneSet = RecipeTransferUtil.getRecipeTransferOperations(
                stackHelper,
                availableItemStacks,
                inputSlots,
                craftingSlots
        );
        if (!oneSet.missingItems.isEmpty()) {
            return false;
        }

        List<Requirement> requirements = new ArrayList<>(oneSet.results.size());
        for (var operation : oneSet.results) {
            Slot sourceSlot = operation.inventorySlot(container);
            Slot targetSlot = operation.craftingSlot(container);
            ItemStack sourceStack = sourceSlot.getItem();
            if (sourceStack.isEmpty()) {
                return false;
            }
            requirements.add(new Requirement(sourceSlot, targetSlot, sourceStack.copyWithCount(operation.count()), targetSlot.getMaxStackSize(sourceStack)));
        }

        List<PlannedMove> plannedMoves = planMoves(player, inventorySlots, requirements, maxTransfer, requireCompleteSets);
        if (plannedMoves.isEmpty()) {
            return false;
        }

        plannedMoves = combineMoves(plannedMoves);
        List<PlannedMove> stackMoves = maxTransfer
                ? planStackMoves(player, inventorySlots, requirements, plannedMoves)
                : List.of();
        boolean moveStacks = !stackMoves.isEmpty() && canMoveStacks(container, requirements, stackMoves);
        if (moveStacks) {
            plannedMoves = stackMoves;
        }
        for (PlannedMove plannedMove : plannedMoves) {
            click(container.containerId, plannedMove.sourceSlot(), 0, ContainerInput.PICKUP, player);
            if (moveStacks) {
                click(container.containerId, plannedMove.targetSlot(), 0, ContainerInput.PICKUP, player);
            } else {
                for (int i = 0; i < plannedMove.count(); i++) {
                    click(container.containerId, plannedMove.targetSlot(), 1, ContainerInput.PICKUP, player);
                }
            }
            if (!container.getCarried().isEmpty()) {
                click(container.containerId, plannedMove.sourceSlot(), 0, ContainerInput.PICKUP, player);
            }
            if (!container.getCarried().isEmpty()) {
                return false;
            }
        }

        return container.getCarried().isEmpty();
    }

    private static List<PlannedMove> combineMoves(List<PlannedMove> moves) {
        Map<MoveKey, Integer> counts = new LinkedHashMap<>();
        for (PlannedMove move : moves) {
            counts.merge(new MoveKey(move.sourceSlot(), move.targetSlot()), move.count(), Integer::sum);
        }
        List<PlannedMove> combined = new ArrayList<>(counts.size());
        counts.forEach((key, count) -> combined.add(new PlannedMove(key.sourceSlot(), key.targetSlot(), count)));
        return combined;
    }

    private static List<PlannedMove> planStackMoves(
            Player player,
            List<Slot> inventorySlots,
            List<Requirement> requirements,
            List<PlannedMove> moves
    ) {
        Map<Integer, Integer> totals = new HashMap<>();
        for (PlannedMove move : moves) {
            totals.merge(move.targetSlot(), move.count(), Integer::sum);
        }
        Map<Slot, Requirement> targets = new LinkedHashMap<>();
        for (Requirement requirement : requirements) {
            if (totals.getOrDefault(requirement.targetSlot().index, 0) != requirement.maxPerSlot()) {
                return List.of();
            }
            Requirement previous = targets.putIfAbsent(requirement.targetSlot(), requirement);
            if (previous != null && !ItemStack.isSameItemSameComponents(previous.ingredient(), requirement.ingredient())) {
                return List.of();
            }
        }

        Map<Slot, ItemStack> workingStacks = new HashMap<>();
        for (Slot slot : inventorySlots) {
            if (!slot.getItem().isEmpty()) {
                workingStacks.put(slot, slot.getItem().copy());
            }
        }
        List<PlannedMove> stackMoves = new ArrayList<>();
        for (Requirement requirement : targets.values()) {
            int remaining = requirement.maxPerSlot();
            while (remaining > 0) {
                Slot source = findMatchingSlot(player, inventorySlots, workingStacks, requirement);
                if (source == null) {
                    return List.of();
                }
                ItemStack stack = workingStacks.get(source);
                int count = Math.min(remaining, stack.getCount());
                stackMoves.add(new PlannedMove(source.index, requirement.targetSlot().index, count));
                stack.shrink(count);
                remaining -= count;
            }
        }
        return stackMoves;
    }

    private static boolean canMoveStacks(
            AbstractContainerMenu container,
            List<Requirement> requirements,
            List<PlannedMove> moves
    ) {
        Map<Integer, Integer> totals = new HashMap<>();
        for (PlannedMove move : moves) {
            totals.merge(move.targetSlot(), move.count(), Integer::sum);
        }
        for (Requirement requirement : requirements) {
            if (totals.getOrDefault(requirement.targetSlot().index, 0) != requirement.maxPerSlot()) {
                return false;
            }
        }

        // Simulate left clicks before sending any: each must place exactly the planned amount.
        Map<Integer, Integer> remainingSources = new HashMap<>();
        Map<Integer, Integer> filledTargets = new HashMap<>();
        for (PlannedMove move : moves) {
            ItemStack source = container.getSlot(move.sourceSlot()).getItem();
            Slot target = container.getSlot(move.targetSlot());
            if (source.isEmpty() || !target.getItem().isEmpty() || !target.mayPlace(source)) {
                return false;
            }
            int available = remainingSources.computeIfAbsent(move.sourceSlot(), key -> source.getCount());
            int filled = filledTargets.getOrDefault(move.targetSlot(), 0);
            int capacity = target.getMaxStackSize(source) - filled;
            if (move.count() != Math.min(available, capacity)) {
                return false;
            }
            remainingSources.put(move.sourceSlot(), available - move.count());
            filledTargets.put(move.targetSlot(), filled + move.count());
        }
        return true;
    }

    private static List<PlannedMove> planMoves(
            Player player,
            List<Slot> inventorySlots,
            List<Requirement> requirements,
            boolean maxTransfer,
            boolean requireCompleteSets
    ) {
        Map<Slot, ItemStack> workingStacks = new HashMap<>();
        for (Slot inventorySlot : inventorySlots) {
            ItemStack stack = inventorySlot.getItem();
            if (!stack.isEmpty()) {
                workingStacks.put(inventorySlot, stack.copy());
            }
        }

        Map<Slot, Integer> plannedPerTarget = new HashMap<>();
        List<Requirement> activeRequirements = new ArrayList<>(requirements);
        List<PlannedMove> plannedMoves = new ArrayList<>();
        boolean transferAsCompleteSets = requireCompleteSets || !maxTransfer;

        while (!activeRequirements.isEmpty()) {
            List<PlannedMove> setMoves = new ArrayList<>(activeRequirements.size());
            Map<Slot, ItemStack> originals = transferAsCompleteSets ? new HashMap<>() : null;
            Map<Slot, Integer> setCounts = new HashMap<>();

            requirementLoop: for (Requirement requirement : activeRequirements) {
                int count = requirement.ingredient().getCount();
                int planned = plannedPerTarget.getOrDefault(requirement.targetSlot(), 0)
                        + setCounts.getOrDefault(requirement.targetSlot(), 0);
                if (planned + count > requirement.maxPerSlot()) {
                    if (transferAsCompleteSets) {
                        rollback(workingStacks, originals);
                        setMoves.clear();
                        break;
                    }
                    continue;
                }

                int remaining = count;
                while (remaining > 0) {
                    Slot sourceSlot = findMatchingSlot(player, inventorySlots, workingStacks, requirement);
                    if (sourceSlot == null) {
                        if (transferAsCompleteSets) {
                            rollback(workingStacks, originals);
                            setMoves.clear();
                            break requirementLoop;
                        }
                        break;
                    }

                    if (originals != null && !originals.containsKey(sourceSlot)) {
                        originals.put(sourceSlot, workingStacks.get(sourceSlot).copy());
                    }

                    ItemStack sourceStack = workingStacks.get(sourceSlot);
                    int moved = Math.min(remaining, sourceStack.getCount());
                    sourceStack.shrink(moved);
                    if (sourceStack.isEmpty()) {
                        workingStacks.remove(sourceSlot);
                    }
                    setMoves.add(new PlannedMove(sourceSlot.index, requirement.targetSlot().index, moved));
                    setCounts.merge(requirement.targetSlot(), moved, Integer::sum);
                    remaining -= moved;
                }
            }

            if (setMoves.isEmpty()) {
                break;
            }

            plannedMoves.addAll(setMoves);
            for (PlannedMove setMove : setMoves) {
                Slot targetSlot = findTarget(requirements, setMove.targetSlot());
                if (targetSlot != null) {
                    plannedPerTarget.merge(targetSlot, setMove.count(), Integer::sum);
                }
            }

            Iterator<Requirement> iterator = activeRequirements.iterator();
            while (iterator.hasNext()) {
                Requirement requirement = iterator.next();
                if (plannedPerTarget.getOrDefault(requirement.targetSlot(), 0) >= requirement.maxPerSlot()) {
                    iterator.remove();
                }
            }

            if (!maxTransfer) {
                break;
            }
        }

        return plannedMoves;
    }

    private static Slot findMatchingSlot(
            Player player,
            List<Slot> inventorySlots,
            Map<Slot, ItemStack> workingStacks,
            Requirement requirement
    ) {
        Slot hintSlot = requirement.hintSlot();
        ItemStack hintStack = workingStacks.get(hintSlot);
        if (matches(player, hintSlot, hintStack, requirement.ingredient())) {
            return hintSlot;
        }

        for (Slot inventorySlot : inventorySlots) {
            ItemStack stack = workingStacks.get(inventorySlot);
            if (matches(player, inventorySlot, stack, requirement.ingredient())) {
                return inventorySlot;
            }
        }

        return null;
    }

    private static boolean matches(Player player, Slot slot, ItemStack stack, ItemStack ingredient) {
        return stack != null
                && !stack.isEmpty()
                && ItemStack.isSameItemSameComponents(stack, ingredient)
                && slot.allowModification(player);
    }

    private static void rollback(Map<Slot, ItemStack> workingStacks, Map<Slot, ItemStack> originals) {
        for (Map.Entry<Slot, ItemStack> entry : originals.entrySet()) {
            workingStacks.put(entry.getKey(), entry.getValue());
        }
    }

    private static Slot findTarget(List<Requirement> requirements, int slotIndex) {
        for (Requirement requirement : requirements) {
            if (requirement.targetSlot().index == slotIndex) {
                return requirement.targetSlot();
            }
        }
        return null;
    }

    private static void click(int containerId, int slotNum, int buttonNum, ContainerInput input, Player player) {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            Field gameModeField = minecraftClass.getField("gameMode");
            Object gameMode = gameModeField.get(minecraft);
            if (gameMode == null) {
                throw new IllegalStateException("Minecraft gameMode is null");
            }

            Method handleContainerInput = gameMode.getClass().getMethod(
                    "handleContainerInput",
                    int.class,
                    int.class,
                    int.class,
                    ContainerInput.class,
                    Player.class
            );
            handleContainerInput.invoke(gameMode, containerId, slotNum, buttonNum, input, player);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to execute vanilla menu click", e);
        }
    }

    private record Requirement(Slot hintSlot, Slot targetSlot, ItemStack ingredient, int maxPerSlot) {
    }

    private record MoveKey(int sourceSlot, int targetSlot) {
    }

    private record PlannedMove(int sourceSlot, int targetSlot, int count) {
    }
}
