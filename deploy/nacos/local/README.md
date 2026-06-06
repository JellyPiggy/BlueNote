# Nacos local 配置目录

后端应用骨架生成后，在本目录补充 local 环境配置：

```text
bluenote-gateway-app.yml
bluenote-member-app.yml
bluenote-content-app.yml
bluenote-social-app.yml
bluenote-im-app.yml
bluenote-order-app.yml
```

敏感值不要直接写入仓库；优先通过环境变量、未提交的本地配置文件或后续密钥管理能力注入。
