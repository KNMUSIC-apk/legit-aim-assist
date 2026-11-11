package com.example.legitaim.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(targets = "net.minecraft.client.ClientBrandRetriever")
public class ClientBrandRetrieverMixin {

    /**
     * @author LegitAim
     * @reason Buộc client báo cáo tên thương hiệu với server là "vanilla" thay vì "fabric", 
     * giúp qua mặt toàn bộ các lệnh kiểm tra client brand từ phía server anti-cheat.
     */
    @Overwrite
    public static String getClientModName() {
        return "vanilla";
    }
}
