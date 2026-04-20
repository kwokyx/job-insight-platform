# VMware 虚拟机节点落地包

本目录用于把 `crawler-node` 落地到 VMware 虚拟机。

## 目录说明

- `env.vmware-nat.example`
- `env.vmware-hostonly.example`
- `env.vmware-bridged.example`
- `install_vm_node.ps1`
- `start_vm_node.ps1`
- `test_vm_node_connectivity.ps1`

## 选择哪一个 env

- 如果虚拟机使用 VMware NAT，优先使用 `env.vmware-nat.example`
- 如果虚拟机使用 VMware Host-Only，优先使用 `env.vmware-hostonly.example`
- 如果虚拟机使用桥接网络，优先使用 `env.vmware-bridged.example`

## 主机地址

当前主机检测到的地址：

- 桥接网卡地址：`192.168.61.64`
- VMware VMnet1：`192.168.72.1`
- VMware VMnet8：`192.168.200.1`

## 虚拟机内最少操作

1. 把 `integrations/distributed-data-acquisition/crawler-node` 整个目录复制到虚拟机
2. 把本目录中合适的 env 模板复制为 `crawler-node/.env`
3. 在虚拟机 PowerShell 执行 `install_vm_node.ps1`
4. 先执行 `test_vm_node_connectivity.ps1`
5. 再执行 `start_vm_node.ps1`

## 推荐顺序

如果你不确定虚拟机网络模式，先在虚拟机里试：

- `ping 192.168.200.1`
- `ping 192.168.72.1`
- `ping 192.168.61.64`

能通哪个，就优先用对应模板。
