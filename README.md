# QSNotice
即时事件通知,可替代eventbus
## Copy and paste just do it! 复制粘贴就是干!
复制java文件到自己项目,改下顶部包名即可使用
```
    //可在base基类注册
    public void onCreate(Bundle savedInstanceState) {
        QSNotice.registerAction(this);
    }
    //解绑
    public void onDestroy() {
        super.onDestroy();
        QSNotice.removeAction(this);
    }
    
    ----------------------------
    
    //订阅通知注解1,必须有一个参数,不能有泛型
    @QSNotice.Action()
    public void noticeLogin(UserInfo event) {
    }
    //发送者
    QSNotice.sendNotice(new UserInfo());
     
    //订阅通知注解2,使用指定action,参数可为一个任意参数或者空
    @QSNotice.Action("action.login")
    public void noticeLogin(UserInfo event) {
    }
    //发送者
    QSNotice.sendNotice("action.login",new UserInfo());
```
### 普通使用
```
    QSNotice.Notice notice = new QSNotice.Notice() {
            @Override
            public void onNotice(String action, Object event) {

            }
    };
    //订阅
    QSNotice.registerNotice(notice, "action.notice");
    //解绑
    QSNotice.removeNotice(notice);
    
    //发送者
    QSNotice.sendNotice("action.notice", "msgobj");

```
