package com.projectkr.shell

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.omarea.common.shared.FilePathResolver
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.ProgressBarDialog
import com.omarea.krscript.config.PageConfigReader
import com.omarea.krscript.config.PageConfigSh
import com.omarea.krscript.model.ClickableNode
import com.omarea.krscript.model.KrScriptActionHandler
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.PageNode
import com.omarea.krscript.model.RunnableNode
import com.omarea.krscript.ui.ActionListFragment
import com.omarea.krscript.ui.ParamsFileChooserRender
import com.projectkr.shell.ui.TabIconHelper
import kotlinx.android.synthetic.main.activity_main.main_tabhost
import kotlinx.android.synthetic.main.activity_main.main_tabhost_2
import kotlinx.android.synthetic.main.activity_main.main_tabhost_3


class MainActivity : AppCompatActivity() {
    private val progressBarDialog = ProgressBarDialog(this)
    private var handler = Handler()
    private var krScriptConfig = KrScriptConfig()

    private fun checkPermission(permission: String): Boolean = PermissionChecker.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeModeState.switchTheme(this)
        setContentView(R.layout.activity_main)

        //supportActionBar!!.elevation = 0f
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        setTitle(R.string.app_name)

        krScriptConfig = KrScriptConfig()


        main_tabhost.setup()
        val tabIconHelper = TabIconHelper(main_tabhost, this)

        main_tabhost.setOnTabChangedListener {
            tabIconHelper.updateHighlight()
        }

        progressBarDialog.showDialog(getString(R.string.please_wait))
        Thread(Runnable {
            val page2Config = krScriptConfig.pageListConfig
            val favoritesConfig = krScriptConfig.favoriteConfig

            val pages = getItems(page2Config)
            val favorites = getItems(favoritesConfig)
            handler.post {
                progressBarDialog.hideDialog()

                if (favorites != null && favorites.size > 0) {
                    updateFavoritesTab(favorites, favoritesConfig)
                    tabIconHelper.newTabSpec(getString(R.string.tab_favorites), ContextCompat.getDrawable(this, R.drawable.tab_favorites)!!, R.id.main_tabhost_2)
                } else {
                    main_tabhost_2.visibility = View.GONE
                }

                if (pages != null && pages.size > 0) {
                    updateMoreTab(pages, page2Config)
                    tabIconHelper.newTabSpec(getString(R.string.tab_pages), ContextCompat.getDrawable(this, R.drawable.tab_pages)!!, R.id.main_tabhost_3)
                } else {
                    main_tabhost_3.visibility = View.GONE
                }
            }
        }).start()

        if (!(checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE) && checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 111)
        }
    }

    private fun getItems(pageNode: PageNode): ArrayList<NodeInfoBase>? {
        var items: ArrayList<NodeInfoBase>? = null

        if (pageNode.pageConfigSh.isNotEmpty()) {
            items = PageConfigSh(this, pageNode.pageConfigSh, null).execute()
        }
        if (items == null && pageNode.pageConfigPath.isNotEmpty()) {
            items = PageConfigReader(this.applicationContext, pageNode.pageConfigPath, null).readConfigXml()
        }

        return items
    }

    private fun updateFavoritesTab(items: ArrayList<NodeInfoBase>, pageNode: PageNode) {
        val favoritesFragment = ActionListFragment.create(items, getKrScriptActionHandler(pageNode, true), null, ThemeModeState.getThemeMode())
        supportFragmentManager.beginTransaction().replace(R.id.list_favorites, favoritesFragment).commitAllowingStateLoss()
    }

    private fun updateMoreTab(items: ArrayList<NodeInfoBase>, pageNode: PageNode) {
        val allItemFragment = ActionListFragment.create(items, getKrScriptActionHandler(pageNode, false), null, ThemeModeState.getThemeMode())
        supportFragmentManager.beginTransaction().replace(R.id.list_pages, allItemFragment).commitAllowingStateLoss()
    }

    private fun reloadFavoritesTab() {
        Thread(Runnable {
            val favoritesConfig = krScriptConfig.favoriteConfig
            getItems(favoritesConfig)?.run {
                handler.post {
                    updateFavoritesTab(this, favoritesConfig)
                }
            }
        }).start()
    }

    private fun reloadMoreTab() {
        Thread(Runnable {
            val page2Config = krScriptConfig.pageListConfig

            getItems(page2Config)?.run {
                handler.post {
                    updateMoreTab(this, page2Config)
                }
            }
        }).start()
    }

    private fun getKrScriptActionHandler(pageNode: PageNode, isFavoritesTab: Boolean): KrScriptActionHandler {
        return object : KrScriptActionHandler {
            override fun onActionCompleted(runnableNode: RunnableNode) {
                if (runnableNode.autoFinish ) {
                    finishAndRemoveTask()
                } else if (runnableNode.reloadPage) {
                    // TODO:多线程优化
                    if (isFavoritesTab) reloadFavoritesTab() else reloadMoreTab()
                }
            }

            override fun addToFavorites(clickableNode: ClickableNode, addToFavoritesHandler: KrScriptActionHandler.AddToFavoritesHandler) {
                val page = when(clickableNode){
                    is PageNode -> clickableNode
                    is RunnableNode -> pageNode
                    else -> return
                }

                val intent = Intent()

                intent.component = ComponentName(this@MainActivity.applicationContext, ActionPage::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)

                if (clickableNode is RunnableNode) {
                    intent.putExtra("autoRunItemId", clickableNode.key)
                }
                intent.putExtra("page", page)

                addToFavoritesHandler.onAddToFavorites(clickableNode, intent)
            }

            override fun onSubPageClick(pageNode: PageNode) {
                openPage(pageNode)
            }

            override fun openFileChooser(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
                return chooseFilePath(fileSelectedInterface)
            }
        }
    }

    private var fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface? = null
    private val ACTION_FILE_PATH_CHOOSER = 65400
    private val ACTION_FILE_PATH_CHOOSER_INNER = 65300

    private fun chooseFilePath(extension: String) {
        try {
            val intent = Intent(this, ActivityFileSelector::class.java)
            intent.putExtra("extension", extension)
            startActivityForResult(intent, ACTION_FILE_PATH_CHOOSER_INNER)
        } catch (ex: java.lang.Exception) {
            Toast.makeText(this, "启动内置文件选择器失败！", Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseFilePath(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(R.string.kr_write_external_storage), Toast.LENGTH_LONG).show()
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 2)
            return false
        } else {
            return try {
                val suffix = fileSelectedInterface.suffix()
                if (!suffix.isNullOrEmpty()) {
                    chooseFilePath(suffix)
                } else {
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.type = fileSelectedInterface.mimeType()
                    if (intent.type == null) {intent.type = "*/*"}
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    startActivityForResult(intent, ACTION_FILE_PATH_CHOOSER)
                }
                this.fileSelectedInterface = fileSelectedInterface
                true
            } catch (ex: java.lang.Exception) {
                false
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ACTION_FILE_PATH_CHOOSER) {
            val result = if (data == null || resultCode != Activity.RESULT_OK) null else data.data
            if (fileSelectedInterface != null && result != null) {
                fileSelectedInterface?.onFileSelected(getPath(result))
            } else {
                fileSelectedInterface?.onFileSelected(null)
            }

            this.fileSelectedInterface = null
        } else if (requestCode == ACTION_FILE_PATH_CHOOSER_INNER) {
            val absPath = if (data == null || resultCode != Activity.RESULT_OK) null else data.getStringExtra("file")
            fileSelectedInterface?.onFileSelected(absPath)
            this.fileSelectedInterface = null
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun getPath(uri: Uri): String? {
        return try {
            FilePathResolver().getPath(this, uri)
        } catch (ex: Exception) {
            null
        }
    }

    fun openPage(pageNode: PageNode) {
        OpenPageHelper(this).openPage(pageNode)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.option_menu_info -> {
                val layoutInflater = LayoutInflater.from(this)
                val layout = layoutInflater.inflate(R.layout.dialog_about, null)
                val transparentUi = layout.findViewById<CompoundButton>(R.id.transparent_ui)
                val themeConfig = ThemeConfig(this)
                transparentUi.setOnClickListener {
                    val isChecked = (it as CompoundButton).isChecked
                    if (isChecked && !checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        it.isChecked = false
                        Toast.makeText(this@MainActivity, R.string.kr_write_external_storage, Toast.LENGTH_SHORT).show()
                    } else {
                        themeConfig.setAllowTransparentUI(isChecked)
                    }
                }
                transparentUi.isChecked = themeConfig.getAllowTransparentUI()

                DialogHelper.customDialog(this, layout)
            }
            R.id.option_menu_reboot -> {
                DialogPower(this).showPowerMenu()
            }

        }
        return super.onOptionsItemSelected(item)
    }
}
